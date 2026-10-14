# Voice agents

A voice agent in jsyn is your code acting for one participant of a call: it listens to
what the caller says and speaks back. The engine gives it one ordered stream of
everything that participant does, the agent event stream. The stream carries speech
activity and each turn from start to end. It adds the turn's words when the participant
has speech to text, an early (eager) end of turn when you turn that on, and what the
caller has heard of the agent when the participant has a speaker.

- [Set up the caller's participant](#set-up-the-callers-participant)
- [The event stream](#the-event-stream)
- [Turns](#turns)
- [Words](#words)
- [Eager end of turn](#eager-end-of-turn)
- [Ending turns and the turn config](#ending-turns-and-the-turn-config)
- [Order, sequence numbers and resuming](#order-sequence-numbers-and-resuming)
- [Keeping up with the stream](#keeping-up-with-the-stream)
- [Problems reported on the stream](#problems-reported-on-the-stream)

The [voice agent example](https://github.com/synauson/examples/tree/main/java/jsyn-voice-agent)
puts all of this together: an agent that answers a simulated caller, streams its replies
as they are written, and stops when the caller talks over it.

## Set up the caller's participant

The agent stream belongs to a participant with turn detection, which needs VAD. Add STT
for the caller's words and a speaker for the agent's voice. The participant can be SIP,
WebRTC, native or file:

```text
.vad(VadConfig.defaults())                    speech started and stopped
.turnDetection(TurnDetectionConfig.defaults()) when the caller has finished a turn
.stt(SttConfig.defaults())                    what they said (optional)
.tts(TtsConfig.defaults())                    the agent's voice (optional)
```

Subscribe with `conf.streamAgentEvents(id, options, observer)`, or leave out the
options for `AgentStreamOptions.defaults()`. A participant without turn detection
throws `AgentStreamException` with reason `TURN_DETECTION_REQUIRED`.

## The event stream

| Event | Fields | When |
|---|---|---|
| `Subscribed` | `oldestSeq`, `lastSeq`, `stt`, `turnConfig`, `tts`, `voice` | First on every subscription: the oldest event the stream keeps, the newest seq, whether STT runs, the turn config in effect, whether there is a speaker and its default voice |
| `Heartbeat` | `conferenceMs`, `sttDecodedMs`, `sttBacklogMs` | When nothing else came for the heartbeat interval (1000 ms, or `withHeartbeatMs`) |
| `SpeechStarted` | `atMs`, `probability` | VAD heard speech start. This is raw voice activity, so noise can start it too. |
| `SpeechStopped` | `atMs`, `speechMs` | VAD heard speech stop; `speechMs` is how long it lasted |
| `TurnStarted` | `turnId`, `startMs`, `wordBacked` | A turn started: with STT at its first word (`wordBacked` true), without STT at VAD's speech start. `startMs` is VAD's speech start either way, except in a late turn ([Turns](#turns)). |
| `TurnWords` | `turnId`, `words`, `sttBacklogMs` | More of the open turn's words ([Words](#words)) |
| `EagerEndOfTurn` | `turnId`, `text`, `words`, `speechEndMs`, `probability`, timings | The turn has probably ended ([Eager end of turn](#eager-end-of-turn)) |
| `TurnResumed` | `turnId`, `cause`, `atMs` | The last `EagerEndOfTurn` was premature, and the turn goes on |
| `EndOfTurn` | `turnId`, `reason`, `text`, `words`, `startMs`, `speechEndMs`, `probability`, `complete`, timings | The turn ended ([Turns](#turns)) |
| `TurnConfigUpdated` | `config` | `updateTurnConfig` changed the turn config |
| `UtteranceStarted`, `WordsPlayed`, `UtteranceDone`, `UtteranceInterrupted`, `UtteranceFailed` | | The speaker's playback ([Text to speech](text-to-speech.md#playback-events)) |
| `Error` | `reason`, `message`, `metadata`, `turnId` | A problem the stream recovers from ([below](#problems-reported-on-the-stream)) |
| `StreamEnded` | `reason` | Last: `PARTICIPANT_REMOVED` or `CONFERENCE_TERMINATED`. `onCompleted` follows. |
| `Unknown` | `type`, `json` | An event kind from a newer engine. Ignore it; it still has a seq. |

Every event also has `conferenceId`, `participantId`, `streamId`, `seq` and
`timestampUnixMs` (wall clock). Times such as `atMs` and `conferenceMs` are conference
time: the conference's clock in milliseconds, the same for all its participants, so you
can compare one participant's speech with another's. `atMs` is when the speech really
started or stopped, so it is earlier than the event by up to VAD's `minSpeechMs` or
`minSilenceMs`.

## Turns

Turn ids count up from 1. Each `TurnStarted` is followed by exactly one `EndOfTurn` for
the same turn, and no event of a turn comes before the previous turn's `EndOfTurn`.
`SpeechStarted` and `SpeechStopped` belong to no turn and arrive as they happen.

Speech after a pause stays in the open turn, unless turn detection decided the pause
ended it: then the turn ends at the pause and the new speech opens the next turn. A turn
ends for one `reason`:

| Reason | When |
|---|---|
| `MODEL` | Turn detection's probability for a pause reached the end-of-turn threshold |
| `TIMEOUT` | The participant stayed silent for the end-of-turn timeout after a speech end (5000 ms by default, 0 turns it off), whatever turn detection said |
| `MANUAL` | You called `conf.forceEndTurn(id)` |
| `STREAM_ENDED` | The participant or the conference went while the turn was open |

With STT, `EndOfTurn` waits for the turn's transcript. `words` are its words, `text`
is them joined by single spaces (the text of its `TranscriptEvent.Turn` without the
leading space), and `complete` is false when STT ran out of drain time first. A turn in
which STT finds no words (a cough, noise) doesn't appear on the stream at all. Without
STT, `text` and `words` are empty and `EndOfTurn` comes at once.

The late words of an incomplete turn open the next turn. If the participant says
nothing more, they arrive once STT has them as a *late turn* of their own:
`TurnStarted`, `TurnWords` and `EndOfTurn`, with no speech of their own. Its `startMs`
is its first word's start and `speechEndMs` its last word's end; its `reason` and
`probability` are the previous turn's (`STREAM_ENDED` when there was none). It has no
`EagerEndOfTurn`, and it is complete unless the participant was removed before STT had
all its words. Every word STT commits reaches a turn, at the end of a file and when the
participant is removed too.

`speechEndMs` is where the turn's speech ended, or when the turn ended if the
participant was still speaking. The timing fields (`sinceSpeechEndMs`, `decisionMs`,
`drainMs`, `sttBacklogMs`) are durations in milliseconds, null when not known.

## Words

With STT, each `Word` has `text` (with its punctuation and no leading space, such as
`Hello,`), `startMs`, `endMs` and `confidence`.

A word goes out in a `TurnWords` once it is complete: when the model has committed the
next word, or the turn has closed. Words therefore trail the speech by about one word,
and the turn's last words come in a `TurnWords` just before its `EndOfTurn`. A turn's
`TurnWords`, in order, are exactly its `EndOfTurn.words`. A word once sent is never
taken back or moved to another turn, so you can start working on a turn's words before
it ends.

Word times are conference time. They are when the model emitted the word, which trails
where it was spoken by up to the model's commit delay: about 800 ms before a pause, and
less in running speech. `startMs` is when the word's first
piece was emitted and `endMs` where its last piece ends.

`confidence` is the lowest of the model's probabilities for the word's pieces, from 0
to 1, or null when the model gives none. It is a relative score, useful to flag a word
worth confirming; it isn't calibrated as the chance that the word is right.

## Eager end of turn

With `eager` on in the turn config, a pause that probably ends the turn sends an
`EagerEndOfTurn` before turn detection confirms it, so you can start on your reply (an
LLM request, say) early:

- With `eagerThreshold` 0 (the default) it comes at VAD's speech end.
- Otherwise it comes when turn detection's probability for the pause reaches
  `eagerThreshold`, which must be at most the end-of-turn threshold.

Its `text` and `words` are what the turn's `EndOfTurn` will carry if this pause ends
the turn. If the participant speaks again and turn detection doesn't end the turn at
the pause, or the committed words differ from the eager text, a `TurnResumed` withdraws
it, with `cause` `SPEECH` or `WORDS`: drop the early work. An `EndOfTurn`'s text equals
the last `EagerEndOfTurn`'s unless a `TurnResumed` came between them. A turn can have
several eager ends, each after a resume.

Don't wait for an eager end before you act on `EndOfTurn`: a turn can end with none.
That happens when `eagerThreshold` is above 0 and the pause's probability reaches the
end-of-turn threshold too, and, with STT, when turn detection ends the turn before STT
has the eager text.

Turn eager on with `TurnConfigUpdate.none().withEager(true)`, at the start with
`TurnDetectionConfig.withTurns` or during the call with `updateTurnConfig`. With STT,
eager also needs decoding set aside on the runtime for the forecasts its text comes
from: `JSynConfig.Builder.sttEager(true)` does that (`capabilities().stt.eager` reports
it), and so does `sttTurnFlush(true)`. Without either, turning eager on for a
participant with STT throws `InvalidArgumentException`.

## Ending turns and the turn config

<!-- snippet: agent-turns -->
```java
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.TurnConfigUpdate;
import java.util.concurrent.BlockingQueue;

/** Answers each of a caller's turns, from events an AgentStreamReader queued. */
public class AgentTurns {
    /** Turn detection for the caller, ending a turn after 3 s of silence at the latest. */
    public static TurnDetectionConfig turnDetection() {
        return TurnDetectionConfig.defaults()
            .withTurns(TurnConfigUpdate.none().withEndOfTurnTimeoutMs(3_000));
    }

    public static void run(Conference conf, String callerId, BlockingQueue<AgentEvent> events)
            throws InterruptedException {
        while (true) {
            AgentEvent event = events.take();
            if (event instanceof AgentEvent.EndOfTurn) {
                AgentEvent.EndOfTurn end = (AgentEvent.EndOfTurn) event;
                // end.reason says why: MODEL, TIMEOUT, MANUAL or STREAM_ENDED.
                answer(end.text);
            } else if (event instanceof AgentEvent.StreamEnded) {
                return;
            }
        }
    }

    /** The caller pressed a key that means "done": end the turn now. */
    public static void doneKey(Conference conf, String callerId) {
        conf.forceEndTurn(callerId); // its EndOfTurn (MANUAL) follows on the stream
    }

    /** Give the caller time to read out a long number. */
    public static void morePatience(Conference conf, String callerId) {
        conf.updateTurnConfig(callerId, TurnConfigUpdate.none().withEndOfTurnTimeoutMs(10_000));
    }

    private static void answer(String text) {
        // Your LLM and TTS go here.
    }
}
```

- `conf.forceEndTurn(id)` ends the open turn now and returns its id. Its `EndOfTurn`
  follows with reason `MANUAL`. With STT the engine forecasts the turn's last words, so
  the text usually comes within about 100 ms on an idle machine, and never later than
  the STT drain budget. If the participant is still speaking, the next turn starts at
  once. It returns empty when no turn was open, or, with STT, when the turn had no word
  yet; such a turn appears on the stream, with its `EndOfTurn`, only if STT then finds
  words in it.
- `conf.updateTurnConfig(id, update)` changes the turn config during the call, for
  example more patience while a caller reads out a number. It returns the config now
  in effect (`AppliedTurnConfig`) with the seq of the `TurnConfigUpdated` that
  announces it.
- `TurnDetectionConfig.withTurns(update)` starts the participant with a turn config
  other than the defaults.

| `TurnConfigUpdate` | Range | Starts at |
|---|---|---|
| `withEndOfTurnThreshold` | 0 to 1 | the `TurnDetectionConfig`'s threshold, 0.5 by default |
| `withEndOfTurnTimeoutMs` | 0 to 60000 ms, 0 is off | 5000 ms |
| `withEager` | | off |
| `withEagerThreshold` | 0 to 1, at most the end-of-turn threshold | 0 |

A value out of range throws `InvalidArgumentException` and leaves the config as it was.
The end-of-turn threshold also decides `TurnDetectionEvent.TurnResult.turnComplete`,
and timeouts and `forceEndTurn` also close a `TranscriptEvent.Turn`. Both calls throw
`FailedPreconditionException` for a participant without turn detection.

## Order, sequence numbers and resuming

Every subscriber sees the events in the same order. `seq` rises by one with each stored
event, from 1. `Subscribed` and `Heartbeat` aren't stored (`isStored()` is false) and
repeat the last seq you have.

`streamId` names the participant's stream: a participant removed and added again under
the same id gets a new stream, with seqs from 1 again. The engine keeps each stream's
last 512 events.

- `AgentStreamOptions.defaults()` replays every event the stream keeps, then goes live.
  If you subscribe with `defaults()` again, you see the kept events again: skip those
  whose `seq` you already handled.
- `AgentStreamOptions.resumeAfter(lastEvent)` continues after the last stored event you
  handled, without losing or repeating one.
- A cursor older than the events kept throws `AgentStreamException` with reason
  `AGENT_REPLAY_EXPIRED`. A cursor from a stream the participant no longer has throws
  `AGENT_STREAM_MISMATCH`. Subscribe again with `defaults()` after either.

## Keeping up with the stream

The observer runs on an engine thread, and the engine never waits for it. An observer
256 events behind is dropped: it gets the events already queued, then `onError` with an
`AgentStreamException` whose reason is `AGENT_SUBSCRIBER_LAGGED` and whose `lastSeq()` is
the last event it got. Hand events to your own queue so this doesn't happen, and resume
when it does. Subscribe again from another thread, never from inside the observer. This
reader resumes after the last event it got when it can; when it has to start over
with `defaults()`, the replay repeats events it already queued, which your agent skips
by `seq`:

<!-- snippet: agent-stream -->
```java
import com.synauson.jsyn.AgentStreamOptions;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.exception.AgentStreamException;
import com.synauson.jsyn.participant.Conference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

/** Reads a participant's agent stream into a queue, resuming it after a lag. */
public class AgentStreamReader implements EventStreamObserver<AgentEvent> {
    private final Conference conf;
    private final String participantId;
    private final BlockingQueue<AgentEvent> events; // your agent's thread takes from it
    private final Executor executor;                // resubscribes off the engine thread
    private volatile AgentEvent lastStored;         // the resume cursor
    private volatile Subscription subscription;

    public AgentStreamReader(Conference conf, String participantId,
                             BlockingQueue<AgentEvent> events, Executor executor) {
        this.conf = conf;
        this.participantId = participantId;
        this.events = events;
        this.executor = executor;
    }

    public void start() {
        subscription = conf.streamAgentEvents(participantId, AgentStreamOptions.defaults(), this);
    }

    @Override
    public void onNext(AgentEvent event) {
        if (event.isStored()) {
            lastStored = event;
        }
        events.add(event); // quick: never block the engine thread
    }

    @Override
    public void onError(Throwable t) {
        if (!(t instanceof AgentStreamException)) {
            return;
        }
        AgentStreamException e = (AgentStreamException) t;
        AgentEvent last = lastStored;
        boolean lagged = e.reason().equals(AgentStreamException.AGENT_SUBSCRIBER_LAGGED);
        executor.execute(() -> {
            subscription.close();
            try {
                // After a lag, continue exactly after the last event this reader got.
                subscription = conf.streamAgentEvents(participantId, lagged && last != null
                    ? AgentStreamOptions.resumeAfter(last)
                    : AgentStreamOptions.defaults(), this);
            } catch (AgentStreamException expired) {
                // AGENT_REPLAY_EXPIRED: what followed the cursor is gone; start afresh.
                subscription = conf.streamAgentEvents(participantId,
                    AgentStreamOptions.defaults(), this);
            }
        });
    }

    @Override
    public void onCompleted() {
        // StreamEnded came first: the participant or the conference is gone.
    }
}
```

## Problems reported on the stream

An `Error` event reports a problem the stream recovers from; the stream goes on. Match
on its `reason`:

| Reason | What happened |
|---|---|
| `TURN_DETECTION_FAILED` | Turn detection stopped. From then on only the timeout or `forceEndTurn` end turns. |
| `TURN_DECISION_MISSING` | A speech end got no turn-detection decision within 2 s. The timeout still ends the turn. |
| `STT_STOPPED` | STT failed. Turns end with the words they had, and later turns have no words. |
| `STT_LAGGING` | STT's backlog passed the drain budget (at least 500 ms; `metadata` has `backlogMs` and `thresholdMs`), so turns may end before their last words, which then open the next turn. It is sent again only after the backlog falls to half. A machine with too little CPU for its STT streams causes it; see [Capacity and sizing](sizing.md). |

`AgentStreamException` ends a subscription, or refuses one, for a reason you can act on:
`TURN_DETECTION_REQUIRED`, `AGENT_REPLAY_EXPIRED`, `AGENT_STREAM_MISMATCH` or
`AGENT_SUBSCRIBER_LAGGED`. Match on `reason()`, never on the message.
