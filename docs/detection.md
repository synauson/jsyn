# Voice activity and turn detection

Two detectors run on any file, SIP, WebRTC or native participant. Voice activity
detection (VAD) reports when the participant starts and stops speaking. Turn detection
decides, at each stop, whether the speaker has finished their turn or is only pausing.
Every license includes them; they need their models in the store ([Licensing and models](licensing-and-models.md)).

- [Turn them on](#turn-them-on)
- [An example](#an-example)
- [VAD settings and events](#vad-settings-and-events)
- [Turn detection settings and events](#turn-detection-settings-and-events)
- [Errors](#errors)

## Turn them on

Put a `VadConfig` on the participant's spec, and a `TurnDetectionConfig` next to it for
turn detection. Turn detection works on VAD's speech ends, so it needs VAD on the same
participant. After adding the participant, subscribe with `conf.streamVadEvents` and
`conf.streamTurnDetectionEvents`.

A participant with turn detection also gets a [voice agent event stream](voice-agents.md),
which combines both detectors, speech to text and the speaker in one ordered stream.
Use that one if you are building an agent.

## An example

This program runs both detectors over a recording, fed through a native participant in
real time as a call's audio would arrive:

<!-- snippet: detection -->
```java
public static void main(String[] args) throws Exception {
    byte[] speech = readWav(new File(args[0]));   // 16 kHz, mono, 16-bit PCM

    try (JSyn jsyn = new JSyn(JSynConfig.builder().build())) { // key from SYNAUSON_LICENSE_KEY
        awaitModels(jsyn, "sentito-1", "fermata-1"); // the VAD and turn-detection models

        try (Conference conf = jsyn.startConference("detection-demo");
             NativeParticipant caller = conf.addNativeParticipant("caller",
                 NativeParticipantSpec.builder()
                     .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                     .vad(VadConfig.defaults())                     // speech start and end
                     .turnDetection(TurnDetectionConfig.defaults()) // needs the VAD above
                     .build());
             // Subscribe after adding: the detectors belong to the participant.
             Subscription vad = conf.streamVadEvents("caller", event -> {
                 if (event instanceof VadEvent.SpeechStart) {
                     System.out.println("speech started");
                 } else if (event instanceof VadEvent.SpeechEnd) {
                     System.out.println("speech ended");
                 }
             });
             Subscription turns = conf.streamTurnDetectionEvents("caller", event -> {
                 TurnDetectionEvent.TurnResult result = (TurnDetectionEvent.TurnResult) event;
                 System.out.printf("turn %s (p=%.2f)%n",
                     result.turnComplete ? "complete" : "not complete", result.probability);
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
            Thread.sleep(1000); // let the last events arrive
        }
    }
}

/** Waits until the runtime has downloaded and verified each model. */
static void awaitModels(JSyn jsyn, String... ids) throws InterruptedException {
    for (int attempt = 0; ; attempt++) {
        Capabilities caps = jsyn.capabilities();
        boolean ready = true;
        for (String id : ids) {
            for (Capabilities.ModelInfo model : caps.models) {
                if (model.id.equals(id) && model.state.equals("not-entitled")) {
                    throw new IllegalStateException(id + " is not licensed now");
                }
                if (model.id.equals(id) && !model.state.equals("ready")) {
                    ready = false;
                    if (attempt % 10 == 0) {
                        System.out.println("waiting for " + id + ": " + model.state);
                    }
                }
            }
        }
        if (ready) {
            return;
        }
        Thread.sleep(1000);
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

On a five-second recording of one spoken sentence it printed:

```text
speech started
speech ended
turn not complete (p=0.09)
```

Your output will differ with the recording: the model judges each pause from the audio
before it, and a probability under the threshold leaves the turn open.

## VAD settings and events

`VadConfig.defaults()` is a threshold of 0.5, 300 ms of silence and 250 ms of speech.
`new VadConfig(threshold, minSilenceMs, minSpeechMs)` sets your own:

| Setting | Meaning | Default |
|---|---|---|
| `threshold` | Speech probability, above 0 and below 1, above which a frame counts as speech | 0.5 |
| `minSilenceMs` | Silence needed before a `SpeechEnd`, 0 to 10000 ms | 300 |
| `minSpeechMs` | Speech needed before a `SpeechStart`, 0 to 10000 ms | 250 |

A value out of range makes the add throw `InvalidArgumentException`.

Events, on `streamVadEvents`:

- `VadEvent.SpeechStart`, with `confidence`, the model's speech probability.
- `VadEvent.SpeechEnd`, with `durationMs`, how long the speech that ended lasted, not
  counting the silence that confirmed its end.

Raw voice activity reacts to any voice-like sound, including noise and other people in
the room. Longer `minSpeechMs` ignores short sounds, and longer `minSilenceMs` keeps
short pauses inside one stretch of speech. Events arrive up to `minSpeechMs` or
`minSilenceMs` after the speech really started or stopped.

## Turn detection settings and events

`TurnDetectionConfig.defaults()` has an end-of-turn threshold of 0.5.
`new TurnDetectionConfig(bufferedSamples, confidenceThreshold)` sets your own
threshold and the audio (16 kHz samples) the participant must have sent before turn
detection decides anything: the default is the model's minimum of 4000 (250 ms),
smaller values mean that minimum, and more than 128000 (the model's 8 s window) is an
`InvalidArgumentException`. Natives 1.7.0 and older ignore `bufferedSamples`.

When VAD reports a speech end, turn detection sends a `TurnDetectionEvent.TurnResult`
on `streamTurnDetectionEvents`: `probability` that the turn is over, and
`turnComplete`, true when the probability reached the end-of-turn threshold. A speech
end that gets no decision in time gets no `TurnResult`; on the agent stream it shows as
`TURN_DECISION_MISSING`.

The threshold can change during a call: `conf.updateTurnConfig` sets a new end-of-turn
threshold and timeout, and `TurnDetectionConfig.withTurns` starts a participant with
them. Both are described in [Voice agents](voice-agents.md#ending-turns-and-the-turn-config).

## Errors

| Error | Cause |
|---|---|
| `InvalidArgumentException: INVALID_ARGUMENT: turn_detection needs vad on the same participant` | A participant with turn detection and no VAD |
| `PermissionDeniedException: LICENSE_REJECTED: cannot add a participant to conference '…': …` (or `LICENSE_DISCONNECTED`, `LICENSE_EXPIRED`) | The license starts nothing new (`license.state` `rejected`, `disconnected` or, for a developer key, `expired`) |
| `FailedPreconditionException` naming `sentito-1` or `fermata-1` | The model hasn't downloaded yet |
| `FailedPreconditionException: LICENSE_SHARE_PENDING: …` | The runtime holds no share of the license's sessions yet (its first usage report hasn't got through); retry shortly |
| `NotFoundException` from `streamVadEvents` or `streamTurnDetectionEvents` | No such participant |
| `FailedPreconditionException` from `streamVadEvents` or `streamTurnDetectionEvents` | The participant has no such detector |
