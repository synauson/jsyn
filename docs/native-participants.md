# Native participants

A native participant is audio that your Java code sends and receives. Use one to put
your own audio source in a call (a bot, a media server you bridge from, a test signal)
or to hand a call's audio to Java code. Audio moves through two shared-memory ring
buffers, so `write` and `read` make no JNI call.

- [Formats](#formats)
- [Send and receive audio](#send-and-receive-audio)
- [Threads and pacing](#threads-and-pacing)
- [Statistics](#statistics)

## Formats

You pick one `NativeAudioFormat` for both directions when you add the participant:

| Format | Sample rate | Bytes per sample | Bytes per 20 ms |
|---|---|---|---|
| `PCM_S16LE8K_MONO` | 8 kHz | 2 | 320 |
| `PCM_S16LE16K_MONO` | 16 kHz | 2 | 640 |
| `PCM_S16LE48K_MONO` | 48 kHz | 2 | 1920 |
| `G711_ULAW8K` | 8 kHz | 1 | 160 |
| `G711_ALAW8K` | 8 kHz | 1 | 160 |

PCM is signed 16-bit little-endian, mono. `format.bytesPer20ms()` gives the frame size.
The engine converts between your format and the conference's.

## Send and receive audio

`write` puts audio into the conference (ingress), and `read` takes out the audio routed
to the participant (egress). This program routes a participant to itself, writes two
seconds of tone and reads it back:

<!-- snippet: native-io -->
```java
public static void main(String[] args) throws Exception {
    NativeAudioFormat format = NativeAudioFormat.PCM_S16LE16K_MONO;
    try (JSyn jsyn = new JSyn(JSynConfig.builder().build()); // key from SYNAUSON_LICENSE_KEY
         Conference conf = jsyn.startConference("native-demo");
         NativeParticipant bot = conf.addNativeParticipant("bot",
             NativeParticipantSpec.builder().format(format).build())) {

        // Route the bot to itself, so read() returns what write() sent.
        // In a real call you route it to and from the caller instead.
        conf.updatePartyAudioConnections(new ConnectionMatrix(
            ConnectionEntry.connect("bot", "bot")));

        byte[] frame = new byte[format.bytesPer20ms()];   // 640 bytes: 20 ms at 16 kHz
        Thread reader = new Thread(() -> {
            byte[] in = new byte[format.bytesPer20ms()];
            long heard = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int n = bot.read(in, 0, in.length);   // 0 when nothing is ready
                    heard += n;
                    if (n == 0) {
                        Thread.sleep(5);
                    }
                }
            } catch (InterruptedException | NativeResourceClosedException stop) {
                // closed or asked to stop
            }
            System.out.println("read " + heard + " bytes");
        });
        reader.start();

        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {                   // 100 frames: 2 seconds
            tone(frame, i, format.sampleRate());
            int written = bot.write(frame, 0, frame.length); // less when the ring is full
            long next = start + (i + 1) * 20_000_000L;      // pace writes in real time
            Thread.sleep(Math.max(0, (next - System.nanoTime()) / 1_000_000));
        }
        reader.interrupt();
        reader.join();

        NativeParticipantStats stats = bot.stats();
        System.out.println("wrote " + stats.bytesWritten + " bytes, "
            + stats.ingressOverruns + " overruns");
    }
}

/** Fills a 16-bit little-endian frame with a 440 Hz tone. */
private static void tone(byte[] frame, int index, int sampleRate) {
    int samples = frame.length / 2;
    for (int s = 0; s < samples; s++) {
        long t = (long) index * samples + s;
        short v = (short) (8000 * Math.sin(2 * Math.PI * 440 * t / sampleRate));
        frame[2 * s] = (byte) v;
        frame[2 * s + 1] = (byte) (v >> 8);
    }
}
```

It prints something like this. The participant hears the mix routed to it, which keeps
running while the conference does, so it reads somewhat more than it wrote:

```text
read 96128 bytes
wrote 64000 bytes, 0 overruns
```

- `write` and `read` never block. `write` returns how many bytes it took, fewer when
  the ingress ring is full. `read` returns how many bytes it gave you, 0 when nothing is
  ready.
- Once you close the `NativeParticipant`, both throw `NativeResourceClosedException`.
  Closing the conference or calling `removeParticipant` doesn't close the Java object:
  `read` then returns 0 and `write` fills the ring. Stop your audio threads, then close
  the participant, as the program above does.
- A native participant can carry detectors, speech to text and a speaker like any
  other. The [speech to text example](speech-to-text.md#an-example) feeds a recording
  through one.

## Threads and pacing

Each ring has one producer and one consumer: at any time, at most one thread may call
`write` and one may call `read` on a participant. Writing and reading on separate
threads is fine, and so are many participants each on its own threads.

Write audio at the pace it plays, one 20 ms frame every 20 ms, as a call's audio would
arrive. The engine reads the ingress ring in real time, so audio written faster than
that fills the ring, and `write` then takes fewer bytes than you offer.

To end a call, stop your audio threads, close the participant, then close the
conference.

## Statistics

`participant.stats()` returns `NativeParticipantStats`: total bytes written and read,
how often each ring overran (`ingressOverruns`, `egressOverruns`), how often the engine
found the ingress ring empty (`ingressUnderruns`), and how many bytes each ring holds
now. A rising `ingressUnderruns` count means you write too slowly, and a rising
`egressOverruns` count means you read too slowly.

Native participants don't count toward `maxParticipantsPerConference`. A native
participant joining a running conference starts in real time, and `muteParticipant`
mutes it like any other participant.
