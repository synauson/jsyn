# Speech to text

Speech to text (STT) transcribes a participant's speech as it arrives, in English. You
get the committed text as it is decoded, and each turn's text when turn detection ends
the turn. STT, like every AI feature, takes one of the license's AI sessions, and it runs on the CPU, so a
machine carries a limited number of STT streams at once ([Capacity and
sizing](sizing.md)).

- [Turn it on](#turn-it-on)
- [An example](#an-example)
- [Deltas and turns](#deltas-and-turns)
- [Settings](#settings)
- [When STT is ready](#when-stt-is-ready)
- [Errors](#errors)

## Turn it on

Add an `SttConfig` to a participant's spec, next to the `TurnDetectionConfig` and
`VadConfig` it needs: each turn's transcript ends where turn detection ends the turn.
Then subscribe with `conf.streamTranscriptEvents(id, observer)`. Events sent before
you subscribe are not replayed.

For a voice agent, read the words from the [agent event stream](voice-agents.md)
instead: its `EndOfTurn` carries the turn's text and timed words, in order with
everything else the caller does.

## An example

This program transcribes a recording, fed through a native participant in real time as
a call's audio would arrive:

<!-- snippet: speech-to-text -->
```java
public static void main(String[] args) throws Exception {
    byte[] speech = readWav(new File(args[0]));   // 16 kHz, mono, 16-bit PCM

    try (JSyn jsyn = new JSyn(JSynConfig.builder().build())) { // key from SYNAUSON_LICENSE_KEY
        awaitStt(jsyn);

        try (Conference conf = jsyn.startConference("stt-demo");
             NativeParticipant caller = conf.addNativeParticipant("caller",
                 NativeParticipantSpec.builder()
                     .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                     .vad(VadConfig.defaults())                     // STT needs turn detection,
                     .turnDetection(TurnDetectionConfig.defaults()) // which needs VAD
                     .stt(SttConfig.defaults())
                     .build());
             Subscription transcript = conf.streamTranscriptEvents("caller", event -> {
                 if (event instanceof TranscriptEvent.Delta) {
                     System.out.print(((TranscriptEvent.Delta) event).text); // as decoded
                 } else if (event instanceof TranscriptEvent.Turn) {
                     System.out.println("\nturn: " + ((TranscriptEvent.Turn) event).text.trim());
                 }
             })) {
            // Send the recording, then a second of silence, 20 ms at a time in real time.
            byte[] frame = new byte[NativeAudioFormat.PCM_S16LE16K_MONO.bytesPer20ms()];
            int frames = speech.length / frame.length + 50;
            long start = System.nanoTime();
            for (int i = 0; i < frames; i++) {
                java.util.Arrays.fill(frame, (byte) 0);
                int from = i * frame.length;
                if (from < speech.length) {
                    System.arraycopy(speech, from, frame, 0,
                        Math.min(frame.length, speech.length - from));
                }
                caller.write(frame, 0, frame.length);
                long next = start + (i + 1) * 20_000_000L;
                Thread.sleep(Math.max(0, (next - System.nanoTime()) / 1_000_000));
            }
            conf.forceEndTurn("caller"); // end a turn still open; its text follows
            Thread.sleep(3000);
        }
    }
}

/** Waits until the STT models have downloaded and the decoding pool has loaded. */
static void awaitStt(JSyn jsyn) throws InterruptedException {
    while (true) {
        Capabilities caps = jsyn.capabilities();
        for (Capabilities.CapabilityInfo c : caps.capabilities) {
            if (c.code.equals("FEATURE_STT") && !c.entitled) {
                throw new IllegalStateException("the license allows no speech to text now");
            }
        }
        String state = caps.stt == null ? "unknown" : caps.stt.state;
        if (state.equals("ready")) {
            return;
        }
        System.out.println("speech to text is " + state + "; waiting");
        Thread.sleep(2000);
    }
}

/** Reads a 16 kHz, mono, 16-bit PCM WAV file into little-endian samples. */
static byte[] readWav(File file) throws Exception {
    try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
        AudioFormat f = in.getFormat();
        if (f.getSampleRate() != 16000 || f.getChannels() != 1 || f.getSampleSizeInBits() != 16
                || f.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || f.isBigEndian()) {
            throw new IllegalArgumentException("need 16 kHz, mono, 16-bit PCM, got " + f);
        }
        return in.readAllBytes();
    }
}
```

On a five-second recording it printed the following, after a few lines of `speech to
text is loading; waiting` while the models downloaded and the decoding pool loaded:

```text
 Hello, this is authentic test fixture for voice activity detection
turn: Hello, this is authentic test fixture for voice activity detection
```

The recording said "a synthetic test fixture": like any speech recognizer, STT makes
mistakes, more so on unusual phrases and poor audio.

## Deltas and turns

`streamTranscriptEvents` sends two kinds of `TranscriptEvent`:

- `TranscriptEvent.Delta` carries newly committed `text` as it is decoded. Committed
  text is never revised, so a participant's deltas concatenate to its transcript.
  Word-initial pieces start with a space. `tentative` is the model's current guess at
  what follows; it replaces any earlier guess and is often empty. `startMs` is where
  the text's first piece starts and `endMs` where its last piece starts, in the
  participant's audio. `decodedMs` is how much audio has been transcribed, and
  `backlogMs` how far decoding is behind the audio.
- `TranscriptEvent.Turn` carries one turn's `text` when the turn ends, with its
  `startMs`, `endMs`, `turnEndMs` and the turn-detection `probability`. A turn ends
  when turn detection decides so, at the end-of-turn timeout, at `forceEndTurn`, or
  when the participant goes; `probability` is 0 for the last three. A turn in which STT
  found no words still sends a `Turn`, with empty text.

The turns' texts add up to the deltas' text, each word once, with two exceptions. A
turn never starts with punctuation: the period that ends one sentence is committed with
the next word, so the turn before ends without it. And with `sttTurnFlush` on, a turn
can repeat a word of the one before (below).

Times are milliseconds of the participant's audio since transcription started on it.
Silence counts, so times line up with the call.

A turn's text may not have settled when turn detection ends the turn, because decoding
runs a little behind the audio. STT waits up to `turnDrainMs` for it. A turn that runs
out of time arrives with `complete` false, and its late words open the next turn. If
no speech follows, they arrive once decoded as a late turn of their own, whose
`turnEndMs` is where the turn before it ended. A late turn is complete unless the
participant was removed before STT had all its words. When the participant is removed,
words no turn took close the turn still open or go out as a late turn, so every
committed word reaches a turn.

## Settings

| Setting | Where | Default | Effect |
|---|---|---|---|
| `turnDrainMs` | `new SttConfig(turnDrainMs)` on the participant | 1000 ms | How long a turn's transcript waits for its text to settle, 0 to 10000 ms |
| `sttTurnFlush(true)` | `JSynConfig.Builder` | off | Closes each turn's transcript as soon as turn detection ends the turn, on a forecast of its last words (below) |
| `sttCapacity(workers, threads, maxStreams)` | `JSynConfig.Builder` | measured | The decoding pool's size and the cap on STT streams; `maxStreams` 0 turns STT off. See [Capacity and sizing](sizing.md). |

With the turn flush on, the engine decodes a forecast of a turn's last words ahead of
the audio, instead of waiting for decoding to catch up. It closed long turns about
130 ms sooner on an 8-core desktop CPU, for about 26% more CPU, and the decoding it sets
aside lowers the STT stream cap by about a quarter. A turn can then arrive before the
deltas of its last words, and if the speaker kept talking, the next turn may repeat a
word.

## When STT is ready

The STT decoding pool loads in the background when the runtime starts, once the
`spartito-1` model is in the store. Until `jsyn.capabilities().stt.state` is `ready`,
adding a participant with STT throws `FailedPreconditionException`. The other states
are `loading`, `idle` (not started: no license or model yet, and `detail` says which)
and `failed` (the next STT participant tries again). On a first start the download and
the load take a few minutes.

`capabilities().stt.streams` shows the cap on STT streams and how many are in use. Each
STT participant takes one stream, and one of the license's AI sessions.

## Errors

| Error | Cause |
|---|---|
| `InvalidArgumentException: INVALID_ARGUMENT: stt needs turn_detection …` | STT without turn detection on the same participant |
| `PermissionDeniedException: LICENSE_REJECTED: cannot add a participant to conference '…': …` (or `LICENSE_DISCONNECTED`, `LICENSE_EXPIRED`) | The license starts nothing new (`license.state` `rejected`, `disconnected` or, for a developer key, `expired`) |
| `FailedPreconditionException` | STT isn't ready yet (above), the `spartito-1` model isn't downloaded, or the runtime holds no share of the license's sessions yet (`LICENSE_SHARE_PENDING`: retry shortly) |
| `LimitExceededException` | Every STT stream this machine admits is in use, or every AI session the license allows |
| `NotFoundException` from `streamTranscriptEvents` | No such participant |
| `FailedPreconditionException` from `streamTranscriptEvents` | The participant has no STT |
