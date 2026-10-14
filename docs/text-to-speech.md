# Text to speech

Text to speech (TTS) gives a participant a speaker: text you send is synthesized and
played into the call, and what the participant has heard is reported on its
[agent event stream](voice-agents.md). It speaks American English and, like every AI
feature, takes one of the license's AI sessions.

- [Give a participant a speaker](#give-a-participant-a-speaker)
- [Speak](#speak)
- [Stop speaking](#stop-speaking)
- [Playback events](#playback-events)
- [Voices and speed](#voices-and-speed)
- [Routing the speaker](#routing-the-speaker)
- [When TTS is ready](#when-tts-is-ready)
- [Errors](#errors)

## Give a participant a speaker

Put `.tts(TtsConfig.defaults())` on the participant's spec, next to the
`TurnDetectionConfig` and `VadConfig` it needs. The speaker plays to its own
participant: the caller on a SIP or WebRTC participant hears it, and a native
participant reads it from its egress ring.

This agent speaks an answer to each of the caller's turns and stops when the caller
talks over it:

<!-- snippet: agent-speak -->
```java
import com.synauson.jsyn.CancelledUtterance;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.spec.Speak;
import com.synauson.jsyn.spec.TtsConfig;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;

/** Speaks an answer to each of the caller's turns and stops when the caller talks over it. */
public class AgentSpeech {
    /** The caller's speaker, set on its spec next to VadConfig and TurnDetectionConfig. */
    public static TtsConfig voice() {
        return TtsConfig.defaults().withVoice("en-us-m1");
    }

    private int replies;

    public void run(Conference conf, String callerId, BlockingQueue<AgentEvent> events)
            throws InterruptedException {
        while (true) {
            AgentEvent event = events.take();
            if (event instanceof AgentEvent.EndOfTurn) {
                String id = "reply-" + (++replies);
                speak(conf, callerId, id, answer(((AgentEvent.EndOfTurn) event).text));
            } else if (event instanceof AgentEvent.SpeechStarted) {
                // The caller talks over the agent: stop, and keep only what they heard.
                for (CancelledUtterance c : conf.cancelUtterances(callerId)) {
                    heard(c.utteranceId, c.heardText);
                }
            } else if (event instanceof AgentEvent.StreamEnded) {
                return;
            }
        }
    }

    /**
     * Stream an LLM's reply as it is written; speech starts after the first words. A real
     * agent runs this off the event loop, so it still sees the caller barge in.
     */
    private static void speak(Conference conf, String callerId, String id, Iterator<String> tokens) {
        while (tokens.hasNext()) {
            conf.speak(callerId, Speak.builder().utteranceId(id).text(tokens.next()).build());
        }
        conf.speak(callerId, Speak.builder().utteranceId(id).release(Speak.Release.END).build());
    }

    private static Iterator<String> answer(String callerSaid) {
        throw new UnsupportedOperationException("your LLM goes here");
    }

    private static void heard(String utteranceId, String heardText) {
        // Record in the conversation what the caller actually heard.
    }
}
```

The events come from an [`AgentStreamReader`](voice-agents.md#keeping-up-with-the-stream).

## Speak

`conf.speak(id, speak)` sends text to the participant's speaker and returns once the
speaker has it, without waiting for synthesis. It returns `true` when this call started
the utterance.

- An utterance is identified by its `utteranceId`, an id like any other (1 to 128 of
  `A-Z`, `a-z`, `0-9`, `.`, `_` and `-`). A new id starts an
  utterance, which queues behind any still playing. The same id adds text to it.
  Don't reuse an id: the speaker refuses the ids of its last 256 finished utterances
  with `UTTERANCE_ENDED`.
- `Speak.complete(id, text)` is a whole utterance in one call.
- To stream an LLM's reply, send each piece as it is written with the default
  `Release.NONE`, then end the utterance with `Release.END`. The speaker holds back
  what could still change (a number still being written), and synthesis keeps ahead of
  playback.
- `Release.FLUSH` speaks everything sent so far now, and leaves the utterance open.
- The speaker reads numbers, money, dates and codes the way a person would. `$42.50` is
  read as an amount of money, for example.
- A Speak's text is at most 16 KiB, an utterance's 64 KiB, and a speaker holds at most
  64 utterances.

Options on an utterance's first Speak apply to the whole utterance:

| `Speak.Builder` | Default | Effect |
|---|---|---|
| `interruptible` | true | Whether `cancelUtterance` and `cancelUtterances` may stop it |
| `preemptible` | false | Whether the next new utterance stops it (reason `PREEMPTED`) |
| `voice` | the speaker's | A voice for this utterance |
| `speed` | the speaker's | A rate for this utterance, 0.25 to 4 |

A voice or speed on a later Speak of the same utterance throws
`InvalidArgumentException`; `interruptible` and `preemptible` on a later Speak are
ignored.

## Stop speaking

- `conf.cancelUtterance(id, utteranceId)` stops one utterance, playing or queued.
- `conf.cancelUtterances(id)` stops every interruptible utterance of the participant,
  which is what to do when the caller barges in.

The audio stops within one 20 ms buffer, plus what the participant's mixer already
holds: 20 ms on SIP and 60 ms on WebRTC. Both calls return a `CancelledUtterance` for
each utterance they stopped, with `heardText` (the text up to its last fully played
word), `heardMs` and the `seq` of its `UtteranceInterrupted`, which is already on the
stream when the call returns. Record `heardText` in your conversation history: it is
what the caller actually heard.

## Playback events

Each utterance that plays any audio gets, on the agent stream:

1. one `UtteranceStarted` (`atMs`, `timeToFirstAudioMs`, `egressDelayMs`),
2. `WordsPlayed` as its words finish playing,
3. exactly one of `UtteranceDone` (`audioMs`, `atMs`, `underrunMs`),
   `UtteranceInterrupted` or `UtteranceFailed`.

An utterance canceled or failed before any of its audio played gets only the last
event.

| Event | Detail |
|---|---|
| `UtteranceInterrupted` | `reason` is `CANCELLED` (`cancelUtterance`), `PREEMPTED` (a new utterance started while it was preemptible) or `STREAM_ENDED` (the participant or conference went; it comes before `StreamEnded`). `heardText`, `heardTextEnd` and `heardMs` say how far it got. |
| `UtteranceFailed` | `code` is `TTS_UNAVAILABLE`, `SYNTHESIS_FAILED`, `UNSPEAKABLE_TEXT` (nothing to say, or a word too long for the model) or `TTS_CAPACITY` (the machine's TTS capacity was in use when it was due to start) |
| `UtteranceDone` | `underrunMs` is how long playback waited for synthesis. On a machine sized for its load it is 0. |

"Played" means the audio passed the speaker's clock-synced point, the last place the
engine can stop it, so `heardText` is exact. The audio leaves the engine about
`egressDelayMs` later, plus the network and the far end's jitter buffer.

A `PlayedWord` has `text` (as sent, with its punctuation; an entity read as several
words, such as `$42.50`, is one word), `textStart` and `textEnd` (its place in the
utterance's text, everything its Speaks sent), and `startMs` and `endMs` (where it
sounds, in milliseconds from the utterance's first sample).

The text offsets count Unicode code points, while Java strings index UTF-16 chars. The
two differ after an emoji or any other character outside the Basic Multilingual Plane,
so use `word.charStart(text)` and `word.charEnd(text)` to index a Java string.
`heardText` is a prefix of the text, so `heardText.length()` is already the char index
where the unheard text starts; `heardTextEnd` is the same place in code points.

## Voices and speed

`TtsConfig` sets the speaker's defaults; each `with*` method returns a copy:

| `TtsConfig` | Default | Values |
|---|---|---|
| `withVoice` | `en-us-f1` | `en-us-f1` to `en-us-f11` (female) and `en-us-m1` to `en-us-m9` (male), American English |
| `withSpeed` | 1 | 0.25 to 4; 1 is the voice's own rate |
| `withSpeakerId` | `<participant id>.speaker` | The speaker's id as a routing source |

An unknown voice or a speed out of range makes the add throw `InvalidArgumentException`.

## Routing the speaker

The speaker always plays to its own participant. It is also a routing source of its
own, so other participants can hear it too:

<!-- snippet: route-speaker -->
```java
// "caller" has a speaker with the default id; a supervisor hears the agent too.
conf.updatePartyAudioConnections(new ConnectionMatrix(
    ConnectionEntry.connect("caller.speaker", "supervisor")));
```

A route from a speaker to its own participant is refused with
`InvalidArgumentException`. A file participant has no output, so a speaker on one
plays only where you route it.

## When TTS is ready

The TTS engine loads in the background when the runtime starts, once the `lettura-2`
model is in the store. Until `jsyn.capabilities().tts.state` is `ready`, adding a
participant with a speaker throws `FailedPreconditionException`. The other states are
`loading`, `idle` (not started: no license or model yet, and `detail` says which) and
`failed` (the next participant with a speaker tries again).

`capabilities().tts.streams` shows the cap on utterances synthesized at once and how
many are in use. The cap counts utterances while they play, however many speakers there
are. Set it, and the pool behind it, with `JSynConfig.Builder.ttsCapacity`; `maxStreams` 0 turns TTS off
([Capacity and sizing](sizing.md#text-to-speech)).

## Errors

The engine's reason leads each message, such as `TTS_REQUIRED: participant 'p' has no
speaker: ...`:

| Exception | Reason | Cause |
|---|---|---|
| `FailedPreconditionException` | `TTS_REQUIRED` | The participant has no speaker |
| `FailedPreconditionException` | `UTTERANCE_ENDED` | More text for an utterance that ended, finished, was interrupted or failed |
| `FailedPreconditionException` | `UTTERANCE_NOT_INTERRUPTIBLE` | `cancelUtterance` on an utterance spoken with `interruptible(false)` |
| `NotFoundException` | `UNKNOWN_UTTERANCE` | An utterance id the speaker never had |
| `InvalidArgumentException` | `INVALID_ID` | An empty or overlong utterance id, or one with characters outside `A-Z a-z 0-9 . _ -` |
| `InvalidArgumentException` | `INVALID_SPEAK` | Text over its cap, a voice or speed after the first Speak, an unknown voice, a speed out of range, or more than 64 utterances held |
| `InvalidArgumentException` | `INVALID_ARGUMENT` | A speaker without turn detection on the participant |
| `PermissionDeniedException` | `LICENSE_REJECTED`, `LICENSE_DISCONNECTED`, `LICENSE_EXPIRED` | Adding the participant when the license starts nothing new (`license.state` `rejected`, `disconnected` or, for a developer key, `expired`) |
| `FailedPreconditionException` | `MODEL_UNAVAILABLE` | Adding a speaker to a runtime with TTS turned off |
| `LimitExceededException` | `SESSION_LIMIT_REACHED` | A participant with AI when every AI session the license allows is in use |
| `FailedPreconditionException` | `LICENSE_SHARE_PENDING` | A participant with AI before this runtime holds a share of the license's sessions (its first usage report hasn't got through yet); retry shortly |
