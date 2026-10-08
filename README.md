# jsyn

[![ci](https://github.com/synauson/jsyn/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/synauson/jsyn/actions/workflows/ci.yml)

jsyn is the Java SDK for the [Synauson](https://synauson.com) media engine. It runs the
engine inside your JVM through JNI, so there is no separate server to deploy. The engine
includes GStreamer media pipelines, an audio router, and ONNX models for voice
activity, end of turn, speech-to-text and text-to-speech. From Java you create
conferences, connect SIP, WebRTC, file and in-process audio participants, route audio
between them, receive detector, transcript and signalling events, and speak into calls.

- [Requirements](#requirements)
- [Install](#install)
- [Quickstart](#quickstart)
- [Concepts](#concepts)
- [Use cases, shown by the tests](#use-cases-shown-by-the-tests)
- [Complete applications](#complete-applications)
- [Configuration and logging](#configuration-and-logging)
- [Troubleshooting](#troubleshooting)
- [Versions](#versions)
- [Building and testing jsyn](#building-and-testing-jsyn)

## Requirements

| | |
|---|---|
| Java | 11 or newer |
| Platforms | Linux x86_64 (glibc 2.34 or newer), Windows 10 or 11 x86_64. macOS and ARM are not supported. |
| GStreamer | 1.26 is recommended and 1.24 is the minimum. **1.28 is not supported**: it changed the `webrtcbin` pad API, which breaks WebRTC. On Linux this decides the distribution: Ubuntu 24.04 and Debian 13 work; Ubuntu 26.04 and Fedora 44 (1.28), Debian 12 and Ubuntu 22.04 (too old) don't. |
| Visual C++ runtime | Windows only: the latest Microsoft Visual C++ v14 Redistributable, x64 ([`vc_redist.x64.exe`](https://aka.ms/vc14/vc_redist.x64.exe)). The natives link against it, and a clean Windows install lacks it. |
| ONNX Runtime | Nothing to install. 1.24.4 ships inside the `jsyn-natives-*` jar. |
| GPU | Not used: inference runs on the CPU, and no NVIDIA software is needed. GPU support is planned. |
| License key | Required. Free-tier keys work. Get one at [synauson.com](https://synauson.com). |
| Network | At startup the engine exchanges the key at `license.synauson.com` and downloads the models your license includes from Cloudflare R2 (`*.r2.cloudflarestorage.com`). See [offline hosts](#licensing-and-models) if the host has no internet access. |

[`docs/install.md`](docs/install.md) has the full steps, the supported Linux
distributions, firewall rules, hardware sizing for speech-to-text, and installation
troubleshooting. In short:

### Linux

On Ubuntu 24.04 or Debian 13, install the GStreamer runtime:

```bash
sudo apt-get install -y libgstreamer1.0-0 gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-nice \
    gstreamer1.0-tools
```

`gstreamer1.0-nice` provides ICE for WebRTC and is easy to miss. `gstreamer1.0-tools`
provides `gst-inspect-1.0` for this check:

```bash
gst-inspect-1.0 --version
gst-inspect-1.0 --exists errorignore && gst-inspect-1.0 --exists webrtcbin \
    && gst-inspect-1.0 --exists nicesrc && gst-inspect-1.0 --exists dtmfdetect && echo ok
```

### Windows

1. Install the Visual C++ runtime,
   [`vc_redist.x64.exe`](https://aka.ms/vc14/vc_redist.x64.exe).
2. Install the GStreamer 1.26.7 MSVC runtime installer,
   [`gstreamer-1.0-msvc-x86_64-1.26.7.msi`](https://gstreamer.freedesktop.org/data/pkg/windows/1.26.7/msvc/gstreamer-1.0-msvc-x86_64-1.26.7.msi),
   system-wide with the Complete profile, to `C:\gstreamer\1.0\msvc_x86_64`. You do not
   need the devel installer.
3. In an elevated PowerShell, set `GSTREAMER_1_0_ROOT_MSVC_X86_64` and add `bin` to the
   machine `Path`:
   ```powershell
   [Environment]::SetEnvironmentVariable("GSTREAMER_1_0_ROOT_MSVC_X86_64", "C:\gstreamer\1.0\msvc_x86_64", "Machine")
   $p = [Environment]::GetEnvironmentVariable("Path", "Machine")
   [Environment]::SetEnvironmentVariable("Path", "$p;C:\gstreamer\1.0\msvc_x86_64\bin", "Machine")
   ```
4. Sign out and back in. Then, once per Windows user, build GStreamer's plugin registry
   with `gst-inspect-1.0.exe coreelements`. If you skip this, the first `new JSyn(...)`
   performs the scan, which took 7 to 44 seconds on fresh CI machines.

For a complete Gradle project, see the
[Windows quickstart](https://github.com/synauson/examples/tree/main/java/jsyn-windows-quickstart).

## Install

jsyn is split into a pure-Java API jar plus one natives jar per platform. All of them come
from the public Synauson Maven repository, which needs no credentials. You can put both
natives jars on the classpath; jsyn loads the one that matches the running OS.

| Artifact | Contents |
|---|---|
| `com.synauson:jsyn` | The API: `JSyn`, `Conference`, specs, events, handles |
| `com.synauson:jsyn-natives-linux` | `libsynauson_jni.so` and ONNX Runtime, Linux x86_64 |
| `com.synauson:jsyn-natives-windows` | `synauson_jni.dll` and ONNX Runtime, Windows x86_64 |

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://maven.synauson.com/releases") }
}

val jsynVersion = "1.5.0"
val jsynNativesVersion = "1.5.1" // see "Versions" below

dependencies {
    implementation("com.synauson:jsyn:$jsynVersion")
    runtimeOnly("com.synauson:jsyn-natives-linux:$jsynNativesVersion")
    runtimeOnly("com.synauson:jsyn-natives-windows:$jsynNativesVersion")
}
```

Maven:

```xml
<properties>
  <jsyn.version>1.5.0</jsyn.version>
  <jsyn.natives.version>1.5.1</jsyn.natives.version>
</properties>

<repositories>
  <repository>
    <id>synauson</id>
    <url>https://maven.synauson.com/releases</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.synauson</groupId>
    <artifactId>jsyn</artifactId>
    <version>${jsyn.version}</version>
  </dependency>
  <dependency>
    <groupId>com.synauson</groupId>
    <artifactId>jsyn-natives-linux</artifactId>
    <version>${jsyn.natives.version}</version>
    <scope>runtime</scope>
  </dependency>
  <!-- and/or jsyn-natives-windows -->
</dependencies>
```

jsyn depends only on Gson and the JSpecify annotations.

## Quickstart

The program below plays a WAV file into a conference and prints each playback event. Set
`SYNAUSON_LICENSE_KEY`, then run it with the path to a WAV file as its argument.

<!-- snippet: quickstart -->
```java
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.FileEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.spec.FileParticipantSpec;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Quickstart {
    public static void main(String[] args) throws Exception {
        String uri = Path.of(args[0]).toUri().toString(); // a WAV file -> file:///...

        JSynConfig config = JSynConfig.builder()
            .licenseKey(System.getenv("SYNAUSON_LICENSE_KEY"))
            .build();

        try (JSyn jsyn = new JSyn(config);
             Conference conf = jsyn.startConference("quickstart")) {
            CountDownLatch done = new CountDownLatch(1);

            // Subscribe before adding the participant so no event is missed.
            // Events arrive on a native thread: hand work off, don't block it.
            try (Subscription events = conf.streamFileEvents("player", event -> {
                System.out.println(event.getClass().getSimpleName());
                if (event instanceof FileEvent.Eos || event instanceof FileEvent.FileError) {
                    done.countDown();
                }
            })) {
                conf.addFileParticipant(FileParticipantSpec.builder()
                    .id("player")
                    .uri(uri)
                    .build());
                done.await(60, TimeUnit.SECONDS);
            }
        } // closing the conference, then the runtime, frees every native resource
    }
}
```

The build compiles this code from
[`Quickstart.java`](jsyn/src/test/java/com/synauson/jsyn/docs/Quickstart.java), and CI
fails if this README falls out of step with it. For more, see
[the tests that show each feature](#use-cases-shown-by-the-tests). The API reference is
the javadoc, which is published with every release as `jsyn-<version>-javadoc.jar` (most
IDEs attach it automatically). To build it locally, run `./gradlew :jsyn:javadoc`.

## Concepts

`JSyn` is the runtime. Constructing it loads the natives, checks GStreamer, validates
the license, and starts downloading models in the background. Create one per process and
share it. GStreamer and ONNX Runtime are process-wide, so a second instance gains you
nothing.

A `Conference` holds participants. `jsyn.startConference(id)` returns one.
`conf.add*Participant(spec)` adds a participant from a builder-made spec. `build()`
throws `InvalidArgumentException` naming every required field you left out. Optional
settings you leave unset take the defaults documented in the javadoc.

| Participant | Spec | Use it for |
|---|---|---|
| File | `FileParticipantSpec` | Playing a GStreamer URI (`file:///…`), optionally in a loop: prompts, hold music |
| Recording | `RecordingParticipantSpec` | Writing one participant's audio to a WAV file |
| SIP | `SipParticipantSpec`, or `SipReservationSpec` then `SipConnectionSpec` | An RTP leg with PCMU/PCMA, DTMF and optional SRTP. Use one-step add when the peer's media is known (inbound), or reserve then connect for an outbound offer/answer. |
| WebRTC | `WebRtcParticipantSpec` | A browser peer: SDP offer in, answer out, trickle ICE |
| Native | `NativeParticipantSpec` | Your own Java audio source and sink, through `write`/`read` on shared-memory rings |

Routing is explicit. Adding participants connects nothing. Audio flows only along the
one-way connections you set with `conf.updatePartyAudioConnections(new
ConnectionMatrix(...))`, and each call replaces the whole matrix. A participant with
nothing connected to it may carry no audio at all. That is why the detector tests
connect a participant to itself.

To run a detector, put `VadConfig` (voice activity) or `TurnDetectionConfig` (end of
turn) on a participant's spec. The detectors form a chain, VAD, then turn detection, then
STT, and each needs the one before it on the same participant: Turn detection without
`VadConfig` throws `InvalidArgumentException` ("turn_detection needs vad on the same
participant"), and nothing is added for you. Then subscribe with
`conf.streamVadEvents(id, observer)` after adding the participant, since the detector
belongs to it. The same pattern works for turn detection, file, DTMF and ICE-candidate
events. Each `stream*` call returns a `Subscription`. Observers run on
an engine thread, so don't block in them; `onError` and `onCompleted` have defaults.

Streaming speech-to-text works the same way: add `SttConfig` next to `TurnDetectionConfig`
and `VadConfig` (STT without turn detection throws `InvalidArgumentException`) and read
`conf.streamTranscriptEvents(id, observer)`. `TranscriptEvent.Delta` carries committed
text as it is decoded, never revised. `TranscriptEvent.Turn` carries one turn's text
when turn detection completes the turn; the turns' texts add up to the deltas' text, each
word once, except that a turn never starts with punctuation (the period ending the
sentence before, which the model commits with the next word), so turns may end without
one. If the turn's text hasn't settled within `SttConfig.turnDrainMs` (default
1000 ms), the turn arrives with `complete == false` and its late words open the next
turn. `JSynConfig.Builder.sttTurnFlush(true)` closes such turns sooner, on a forecast of
their last words. A turn can then arrive before the deltas of its last words, and when
the speaker kept talking and the transcript changed, the next turn may repeat a word
(see the `TranscriptEvent.Turn` javadoc). STT needs `FEATURE_STT` in the license, which
includes turn detection and VAD. Its
decoding pool loads in the background when the runtime starts; until
`capabilities().stt.state` is `ready`, adding a participant with STT throws
`FailedPreconditionException`. `capabilities().stt` also reports how many STT streams
the machine transcribes in real time, as measured; `JSynConfig.Builder.sttCapacity`
overrides it. [Sizing for STT](docs/install.md#sizing-for-stt) gives measured figures
per CPU.

Everything that owns native memory is `AutoCloseable`: `JSyn`, `Conference`, `Subscription` and
`NativeParticipant`. Close them in reverse order of creation, which
try-with-resources does for you. `close()` is idempotent, and a closed object throws
`NativeResourceClosedException`. An object you forget to close is freed by a cleaner when
it is garbage collected, which can be much later.

`Conference` methods are thread-safe. Each `NativeParticipant` ring has one
producer and one consumer: at any time, at most one thread may call `write` and one may
call `read`.

Errors are unchecked subclasses of `JSynException`, named after the failure:
`InvalidArgumentException`, `NotFoundException`, `AlreadyExistsException`,
`FailedPreconditionException`, `PermissionDeniedException`, `LimitExceededException`, and
others. The agent event stream's `AgentStreamException` also carries a stable
`reason()`. The public API is `@NullMarked` (JSpecify): nothing is null unless it is marked
`@Nullable`.

### Voice-agent event stream

An agent that talks with a participant needs one ordered stream of what that
participant does. `conf.streamAgentEvents(id, options, observer)` delivers it as
`AgentEvent`s for any participant with `TurnDetectionConfig` (and the `VadConfig` that
drives it); without turn detection it throws `AgentStreamException` with reason
`TURN_DETECTION_REQUIRED`. It carries speech activity, the turn lifecycle, with STT
each turn's words, when you turn it on an early (eager) end of turn, and with a speaker
what it played (see [Speaking into the call](#speaking-into-the-call)).

| Event | Fields | When |
|---|---|---|
| `Subscribed` | `oldestSeq`, `lastSeq`, `stt`, `turnConfig`, `tts`, `voice` | First on every subscription: the oldest event the stream keeps, its newest seq, whether STT runs, the turn config in effect, whether the participant has a speaker and its default voice |
| `Heartbeat` | `conferenceMs`, `sttDecodedMs`, `sttBacklogMs` | Whenever nothing else came for the heartbeat interval (default 1000 ms, `withHeartbeatMs`) |
| `SpeechStarted` | `atMs`, `probability` | VAD heard speech start. Raw voice activity: noise can start it too. |
| `SpeechStopped` | `atMs`, `speechMs` | VAD heard it stop; `speechMs` is how long it lasted |
| `TurnStarted` | `turnId`, `startMs`, `wordBacked` | A turn started: with STT on its first word (`wordBacked` true), without STT on VAD's speech start. `startMs` is VAD's speech start either way. |
| `TurnWords` | `turnId`, `words`, `sttBacklogMs` | More of the open turn's words, each once it is complete (below) |
| `EagerEndOfTurn` | `turnId`, `text`, `words`, `speechEndMs`, `probability`, latency fields | The turn has probably ended at a pause turn detection hasn't confirmed; only with eager on (below) |
| `TurnResumed` | `turnId`, `cause`, `atMs` | The last `EagerEndOfTurn` was premature: the turn goes on (below) |
| `EndOfTurn` | `turnId`, `reason`, `text`, `words`, `startMs`, `speechEndMs`, `probability`, `complete`, latency fields | The turn ended (below) |
| `TurnConfigUpdated` | `config` | `updateTurnConfig` changed the turn config |
| `UtteranceStarted` | `utteranceId`, `atMs`, `timeToFirstAudioMs`, `egressDelayMs` | The speaker's first sample of an utterance played |
| `WordsPlayed` | `utteranceId`, `words` | More of the utterance's words fully played (`PlayedWord`) |
| `UtteranceDone` | `utteranceId`, `audioMs`, `atMs`, `underrunMs` | The utterance played to its end |
| `UtteranceInterrupted` | `utteranceId`, `reason`, `heardText`, `heardTextEnd`, `heardMs` | It stopped early: `CANCELLED` (`cancelUtterance`), `PREEMPTED` (a new utterance started while it was preemptible) or `STREAM_ENDED` (the participant or conference went; before `StreamEnded`) |
| `UtteranceFailed` | `utteranceId`, `code`, `message` | It failed: `TTS_UNAVAILABLE`, `SYNTHESIS_FAILED`, `UNSPEAKABLE_TEXT` (nothing to say, or a word too long for the model) or `TTS_CAPACITY` (the machine's TTS capacity was in use when it was to start) |
| `Error` | `reason`, `message`, `metadata`, `turnId` | A recoverable problem; the stream goes on. `TURN_DETECTION_FAILED`: Turn detection stopped, so only the timeout or `forceEndTurn` end turns from then on. `TURN_DECISION_MISSING`: a speech end got no turn-detection decision within 2 s; the timeout still ends the turn. `STT_STOPPED`: STT failed, so turns end with the words they had, and later ones without words. `STT_LAGGING`: STT's backlog passed the STT drain budget (at least 500 ms; metadata `backlog_ms`, `threshold_ms`), so turns may end before their last words, which then open the next turn; sent again only after the backlog falls to half. |
| `StreamEnded` | `reason` | Last: the participant was removed or the conference terminated. `onCompleted` follows. |
| `Unknown` | `type`, `json` | A kind from a newer engine. Ignore it, but it still has a seq. |

Every event has `conferenceId`, `participantId`, `streamId`, `seq` and
`timestampUnixMs` (wall clock). Times such as `atMs` and `conferenceMs` are conference
time: the conference's pipeline clock in ms, the same for all its participants, so you
can compare one participant's speech with another's. `atMs` is where the speech really
started or stopped, so it is earlier than the event by up to VAD's `minSpeechMs` or
`minSilenceMs`.

**Turns.** Turn ids rise by one from 1. Each `TurnStarted` is followed by exactly one
`EndOfTurn` for it, and no event of a turn comes before the previous turn's
`EndOfTurn`; `SpeechStarted` and `SpeechStopped` carry no turn and come at once. Speech
after a pause stays in the open turn unless turn detection's decision on the pause says
complete: then the turn ends at the pause and the new speech opens the next. A turn
ends for one `reason`:

- `MODEL`: Turn detection's probability for a pause reached the end-of-turn threshold.
- `TIMEOUT`: the participant stayed silent for the end-of-turn timeout (default 5000 ms,
  0 turns it off) after a speech end, whatever turn detection said or whether it answered.
- `MANUAL`: you called `conf.forceEndTurn(id)`.
- `STREAM_ENDED`: the participant or conference went with the turn open.

With STT, `EndOfTurn` waits for the turn's transcript: `words` are its words, `text` is
them joined by single spaces (the same text as its `TranscriptEvent.Turn`, without the
leading space), and `complete` is false when STT ran out of drain time first. A turn STT
finds no words in (a cough, noise) is not on the agent stream at all. Without STT,
`text` and `words` are empty and `EndOfTurn` comes at once.
`speechEndMs` is where the turn's speech ended (when it ended, if the participant was
still speaking); the latency fields (`sinceSpeechEndMs`, `decisionMs`, `drainMs`,
`sttBacklogMs`) are durations, null when not known.

**Eager end of turn.** With `eager` on in the turn config, a pause that probably ends
the turn sends an `EagerEndOfTurn` before turn detection confirms it: at VAD's speech end
when `eagerThreshold` is 0, else when turn detection's probability for the pause reaches
`eagerThreshold` (at most the end-of-turn threshold). Its `text` and `words` are what the
turn's `EndOfTurn` will carry if this pause ends it, so you can start your reply (an
LLM request, say) early. If the participant speaks again and turn detection doesn't call the
pause the turn's end, or committed words differ from the eager text, a `TurnResumed`
withdraws it (`cause` `SPEECH` or `WORDS`): drop that early work. An `EndOfTurn`'s text
equals the last `EagerEndOfTurn`'s unless a `TurnResumed` came between them. A turn may
have several eager ends, each after a resume. With STT, eager needs
`JSynConfig.Builder.sttEager(true)` on the runtime, which sets decoding aside for the
forecasts its text comes from (`Capabilities.SttCapacity.eager` reports it); without it,
turning eager on for an STT participant throws `InvalidArgumentException`.

**Words.** With STT, each `Word` has `text` (with its punctuation and no leading space,
such as `Hello,`), `startMs`, `endMs` and `confidence`. A word goes out in a `TurnWords`
once it is complete, which is when the model has committed the next word or the turn
has closed, so words trail the speech by about one word. The turn's last words come in
a `TurnWords` just before its `EndOfTurn`. A turn's `TurnWords`, in order, are exactly
its `EndOfTurn.words`: a word once sent is never taken back or moved to another turn,
so you can start working on a turn's words before it ends. Word times are conference
time and are when the model emitted the word, not where it was spoken: they trail the
audio by up to the model's commit delay (about 800 ms for the default model before a
pause, less in running speech). `startMs` is when its first piece was emitted, `endMs`
where its last piece ends. `confidence` is the lowest of the model's probabilities for
the word's pieces, in [0, 1], or null when the model gives none. Read it as a relative
score that flags a word worth confirming, not as a calibrated chance of being right.

**Ending turns and the turn config.** `conf.forceEndTurn(id)` ends the open turn now
and returns its id (empty when none was open, or, with STT, when the turn had no word
yet: such a turn is sent, with its `EndOfTurn`, only if STT then finds words in it);
its `EndOfTurn` follows with reason `MANUAL`. With STT the engine forecasts the turn's last words, so the text usually
comes within about 100 ms on an idle machine and never later than the STT drain
budget. If the participant is still speaking, the next turn starts at once.
`conf.updateTurnConfig(id, update)` changes the end-of-turn threshold or timeout
mid-call (for more patience while a caller reads out a number, say) and returns the
config in effect with the seq of the `TurnConfigUpdated` that announces it. Start a
participant with a config other than the defaults with `TurnDetectionConfig.withTurns`.
Thresholds are in [0, 1] and the timeout 0 to 60000 ms; anything else throws
`InvalidArgumentException`. The threshold
also decides `TurnDetectionEvent.TurnResult.turnComplete`, and timeouts and `forceEndTurn`
also close a `TranscriptEvent.Turn`. Both calls throw `FailedPreconditionException`
for a participant without turn detection.

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

**Order, seq and resume.** Every subscriber sees the events in one order. `seq` rises
by one with each stored event, from 1; `Subscribed` and `Heartbeat` aren't stored
(`isStored()` is false) and repeat the last seq you have. `streamId` names the
participant's stream: a participant removed and added again under the same id gets a
new one, with seqs from 1. The engine keeps each stream's last 512 events.
`AgentStreamOptions.defaults()` replays all of them, then goes live.
`AgentStreamOptions.resumeAfter(lastEvent)` continues after the last stored event you
processed, without losing or repeating one. A cursor older than what is kept throws
`AGENT_REPLAY_EXPIRED`, and one from a stream the participant no longer has throws
`AGENT_STREAM_MISMATCH`; subscribe again with `defaults()`.

**Lag.** The observer runs on an engine thread, and the engine never waits for it. An
observer 256 events behind is dropped: it gets the events already queued, then
`onError` with an `AgentStreamException` whose reason is `AGENT_SUBSCRIBER_LAGGED` and
whose `lastSeq()` is the last one it got. Hand events to your own queue so this
doesn't happen, and resume when it does. Subscribe again from another thread, never
from inside the observer:

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

### Speaking into the call

Give a participant a speaker with `.tts(TtsConfig.defaults())` on its spec, next to the
`TurnDetectionConfig` and `VadConfig` it needs (a speaker without turn detection throws
`InvalidArgumentException`): its playback is reported on the participant's agent
stream. `TtsConfig` sets the default voice (`en-us-f1` unless you pick another: the American
English voices are `en-us-f1` to `en-us-f11` (female) and `en-us-m1` to `en-us-m9`
(male)), the default speed (0.25 to 4) and the speaker's id
as a routing source (`<participant id>.speaker`), so you can also route it to a
recording. The speaker plays to its own participant; a file participant has no output,
so its speaker plays only where you route it. TTS needs `FEATURE_TTS` in the license.
Its engine loads in the background when the runtime starts; until
`capabilities().tts.state` is `ready`, adding a participant with a speaker throws
`FailedPreconditionException`.

`conf.speak(id, speak)` sends text. A new utterance id starts an utterance, which
queues behind any still playing, and the same id adds text to it until a Speak with
`Release.END`; `Speak.complete(id, text)` is a whole utterance in one call. Send an
LLM's reply piece by piece as it is written: the speaker reads numbers, money, dates
and codes as a person would, holds back what could still change (a number still being
written), starts after the first two words, and then keeps synthesis ahead of
playback. `Release.FLUSH` speaks everything sent so far now and leaves the utterance
open. `speak` returns once the speaker has the text, `true` when it started the
utterance. A Speak's text is at most 16 KiB and an utterance's 64 KiB. Each utterance
can be `interruptible` (default true), `preemptible` (default false: the next new
utterance stops it) and have its own `voice` and `speed`, all set on its first Speak.

`conf.cancelUtterance(id, utteranceId)` stops one utterance, playing or queued, and
`conf.cancelUtterances(id)` stops every interruptible one, which is what to do when the
caller barges in. The audio stops within one 20 ms buffer plus what the participant's
mixer holds (20 ms on SIP, 60 ms on WebRTC). Each returns a `CancelledUtterance` per
stopped utterance with `heardText` (the text up to its last fully played word), `heardMs`
and the `seq` of its `UtteranceInterrupted`, which is already on the stream.

**Playback events.** Each utterance that plays any audio gets one `UtteranceStarted`,
then its `WordsPlayed`, then exactly one of `UtteranceDone`, `UtteranceInterrupted` or
`UtteranceFailed`. One cancelled or failed before any of its audio played gets only that
last event. "Played" means the audio passed the speaker's clock-synced point, the last
place the engine can stop it, so `heardText` is exact; the audio leaves the engine about
`egressDelayMs` later, plus the network and the far end's jitter buffer. A `PlayedWord`
has `text` (as sent, with its punctuation; an entity read as several words, such as
`$42.50`, is one word), `textStart` and `textEnd` (its place in the utterance's text,
everything its Speaks sent), and `startMs` and `endMs` (where it sounds, in ms from the
utterance's first sample). The text offsets count **Unicode code points**, while Java
strings index UTF-16 chars, so the two differ after an emoji or any other character
outside the Basic Multilingual Plane: use `word.charStart(text)` and
`word.charEnd(text)`, which call `String.offsetByCodePoints`. `heardText` is a prefix of
the text, so `heardText.length()` is already the char index where the unheard text
starts; `heardTextEnd` is the same place in code points.

The errors lead their exception's message with a reason: `FailedPreconditionException`
for `TTS_REQUIRED` (no speaker), `UTTERANCE_ENDED` (more text for an utterance that
ended, finished, was interrupted or failed: ids are never reused) and
`UTTERANCE_NOT_INTERRUPTIBLE`; `NotFoundException` for `UNKNOWN_UTTERANCE` (an id the
speaker never had); `InvalidArgumentException` for `INVALID_SPEAK` (an empty or overlong
id, text over its cap, a voice or speed after the first Speak, an unknown voice, a speed
out of range, more than 64 utterances held).

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

### Licensing and models

The license key comes from `JSynConfig.licenseKey`, or from `$SYNAUSON_LICENSE_KEY` when
that is null. The engine exchanges it for a signed license, caches the license in the
state directory, and renews it about once a day. If the licensing server is unreachable,
the engine starts on the cached license, or else on the free floor.

A license has a **plan** and one number. The plan says what new work may use, and each
plan includes everything in the one below it: `detect` runs VAD and turn detection, `speech`
adds STT and TTS. The number is how many **AI sessions** may run at once: a participant with any
detector takes one session, whatever detectors it has, and conferences and participants
without detectors take none.

- A detector or speaker the plan lacks throws `PermissionDeniedException` naming its
  entitlement code (`FEATURE_VAD`, `FEATURE_TURN_DETECTION`, `FEATURE_STT`,
  `FEATURE_TTS`).
- From 80% of the session limit the engine logs a warning for each new session. Above
  the limit, a burst (25% unless the license sets another) is still admitted and logged
  as overage. Past the burst, adding a participant with a detector throws
  `LimitExceededException`. Nothing already running is ever stopped.
- About 30 days before the license expires the engine logs a daily warning. After it
  expires, everything keeps working for 14 days of grace; then new AI work falls to the
  free floor (`detect`, 4 sessions). Calls in progress continue.

`jsyn.capabilities()` reports all of it: `license.state` (the standing: `valid`,
`expiring`, `grace`, `expired-floor`, `invalid-kept-last-valid`, `free-floor` or
`rejected`), `license.plan`, `license.daysRemaining` and `license.problem`; `sessions`
(limit, in use, the burst's `ceiling`, a `level` from `ok` to `full`, and the peak since
start); each capability (`FEATURE_VAD`, `FEATURE_TURN_DETECTION`, `FEATURE_STT`,
`FEATURE_TTS`, in that order) with `entitled` and `includedBy`; and the state of each
model (`sentito-1` VAD, `fermata-1` turn detection, `spartito-1` STT, `lettura-1` TTS).

The models the plan includes download into the model store in the background: the VAD
and turn-detection models for `detect`; for `speech` also the STT model (about 660 MB) and
the TTS model with its voices and pronunciation data (about 347 MB).
Adding a detector before its model is ready throws `FailedPreconditionException` naming
the model. On a host with no internet access, set `offline(true)` and a `licenseFile`, and
fill the store from a folder of model files with `JSyn.importModels(from, store)`.

The [licensing tour](https://github.com/synauson/examples/tree/main/java/jsyn-licensing)
walks through all of this against a real free license, including how to wait for models
and what to do when a limit is hit.

## Use cases, shown by the tests

jsyn's integration tests run against the real engine on Linux and Windows on every push,
so they are working, current examples of each feature. The helpers they share (the
`JSynTestHelpers` factory, a loopback [RTP peer][SipRtpPeer], and a headless-Chromium
[WebRTC peer][WebRtcBrowserPeer]) are test scaffolding. Everything else is plain jsyn
API.

| Use case | Test | What to look at |
|---|---|---|
| Runtime and conference lifecycle | [JSynLifecycleIT] | try-with-resources nesting, two isolated conferences, `conf.state()` |
| Shutting down with audio in flight | [GracefulShutdownIT] | Close participant, conference and runtime while another thread writes. The writer stops on `NativeResourceClosedException`. |
| Play a file, get playback events | [FileParticipantIT] | Build the URI with `Path.toUri()` so it is valid on Windows too, subscribe before adding, then `PlaybackStarted` and `Eos` |
| Record a participant to WAV | [RecordingParticipantIT], [SipStartOrderE2eIT] | `sourceParticipantId` and `outputPath`; the second test records a live SIP caller |
| Push and pull raw audio from Java | [NativeParticipantBasicIT], [NativeParticipantFormatMatrixIT] | `write`/`read`/`stats`, every `NativeAudioFormat`, and a self-connection that echoes audio back to `read` |
| One audio thread per participant | [NativeParticipantConcurrencyIT] | Five participants written from five threads. A full ring returns 0; it does not throw. |
| Route audio between participants | [SipMixedSourcesE2eIT], [SipReserveConnectE2eIT] | `updatePartyAudioConnections`: growing the matrix mid-call, one destination mixing a native and a SIP source, and a two-way call as two one-way entries |
| Voice activity detection | [VadDetectorIT], [RealVadE2eLatencyIT] | `VadConfig.defaults()`, a self-connection so audio reaches the detector, then `VadEvent.SpeechStart` |
| End-of-turn detection | [TurnDetectionIT] | `TurnDetectionConfig` alongside VAD (without VAD it throws `InvalidArgumentException`), then `TurnDetectionEvent.TurnResult` |
| Voice-agent turns | [AgentTurnsIT] | `TurnDetectionConfig.withTurns`, `forceEndTurn` ending the open turn with `MANUAL`, `updateTurnConfig` answered and announced, and the refusals |
| Voice-agent words | [AgentWordsIT] | STT on (`sttCapacity`, waiting for `capabilities().stt.state` to be `ready`): a word-backed `TurnStarted`, `TurnWords` adding up to `EndOfTurn.words`, `text` as the words joined, word times and confidences |
| Voice-agent speech | [AgentSpeakIT] | A speaker (`TtsConfig`, `ttsCapacity`, waiting for `capabilities().tts.state` to be `ready`): `speak` and its `UtteranceStarted`, `WordsPlayed` slicing the sent text, `UtteranceDone`, then `cancelUtterance` with the `UtteranceInterrupted` it names, and the reasoned refusals |
| Voice-agent event stream | [AgentStreamIT] | `streamAgentEvents`: `Subscribed` first, speech events in conference time, resuming from a cursor, `StreamEnded` on removal, and `TURN_DETECTION_REQUIRED` without turn detection |
| Streaming speech-to-text | [SttIT] | `SttConfig` needs `TurnDetectionConfig`, `streamTranscriptEvents` needs STT on the participant, and `capabilities().stt` |
| Speech-to-text on a WebRTC call | [WebRtcSttE2eIT] | A browser speaking into a participant with VAD, turn detection and STT: wait for `capabilities().stt.state` to be `ready`, subscribe before answering, then read the words from `EndOfTurn.text` and `TranscriptEvent.Turn`, joining turns (a turn that ran out of drain time hands its last words to the next) |
| Model store and missing models | [ModelStoreIT] | `JSyn.importModels` is idempotent and rejects corrupt files. A missing model throws `FailedPreconditionException` and leaves nothing half-built. |
| Inbound SIP call | [SipParticipantIT], [SipMediaE2eIT] | `addSipParticipant`, `localRtpPort()` for your SDP, real RTP both ways, VAD on a SIP caller |
| Outbound SIP call (reserve, then connect) | [SipReserveConnectE2eIT] | Reserve the ports for the offer, connect with the answer's `SipRemoteMedia`, SRTP keys across the two phases, and releasing a reservation |
| DTMF | [SipDtmfE2eIT], [DtmfEventsIT] | `sendDtmf` sends RFC 4733 on the wire, `streamDtmfEvents` receives, and invalid digits are rejected. [SipMixedSourcesE2eIT] also covers in-band DTMF (`dtmfPayloadType(0)`). |
| WebRTC offer/answer | [WebRtcParticipantIT] | The smallest `addWebRtcParticipant` call and `sdpAnswer()` |
| WebRTC with a real browser | [WebRtcMediaE2eIT] | Relay the answer and trickle ICE both ways (`streamWebRtcIceCandidates`, `addIceCandidate`), VAD on browser audio, echo back |
| Voice agent: browser caller and Java agent | [WebRtcNativeParticipantE2eIT] | A WebRTC caller wired both ways to a `NativeParticipant`, ICE candidates handed off the engine thread, and hang-up order |
| Per-call WebRTC options | [WebRtcOptionsE2eIT] | `icePortRange`, Opus options, `stunServer("")` to disable STUN, runtime defaults from `JSynConfig`, `stats().effectiveOptions`, rejected options |

No test covers `muteParticipant`, `addPriorityAudioFiles` or `getResourceSnapshot`; see
their javadoc. `capabilities()` is covered by the licensing tour.

[JSynLifecycleIT]: jsyn/src/test/java/com/synauson/jsyn/it/JSynLifecycleIT.java
[GracefulShutdownIT]: jsyn/src/test/java/com/synauson/jsyn/it/GracefulShutdownIT.java
[FileParticipantIT]: jsyn/src/test/java/com/synauson/jsyn/it/FileParticipantIT.java
[RecordingParticipantIT]: jsyn/src/test/java/com/synauson/jsyn/it/RecordingParticipantIT.java
[SipStartOrderE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/SipStartOrderE2eIT.java
[NativeParticipantBasicIT]: jsyn/src/test/java/com/synauson/jsyn/it/NativeParticipantBasicIT.java
[NativeParticipantFormatMatrixIT]: jsyn/src/test/java/com/synauson/jsyn/it/NativeParticipantFormatMatrixIT.java
[NativeParticipantConcurrencyIT]: jsyn/src/test/java/com/synauson/jsyn/it/NativeParticipantConcurrencyIT.java
[SipMixedSourcesE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/SipMixedSourcesE2eIT.java
[VadDetectorIT]: jsyn/src/test/java/com/synauson/jsyn/it/VadDetectorIT.java
[RealVadE2eLatencyIT]: jsyn/src/test/java/com/synauson/jsyn/it/RealVadE2eLatencyIT.java
[TurnDetectionIT]: jsyn/src/test/java/com/synauson/jsyn/it/TurnDetectionIT.java
[SttIT]: jsyn/src/test/java/com/synauson/jsyn/it/SttIT.java
[AgentStreamIT]: jsyn/src/test/java/com/synauson/jsyn/it/AgentStreamIT.java
[AgentTurnsIT]: jsyn/src/test/java/com/synauson/jsyn/it/AgentTurnsIT.java
[AgentWordsIT]: jsyn/src/test/java/com/synauson/jsyn/it/AgentWordsIT.java
[AgentSpeakIT]: jsyn/src/test/java/com/synauson/jsyn/it/AgentSpeakIT.java
[ModelStoreIT]: jsyn/src/test/java/com/synauson/jsyn/it/ModelStoreIT.java
[SipParticipantIT]: jsyn/src/test/java/com/synauson/jsyn/it/SipParticipantIT.java
[SipMediaE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/SipMediaE2eIT.java
[SipReserveConnectE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/SipReserveConnectE2eIT.java
[SipDtmfE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/SipDtmfE2eIT.java
[DtmfEventsIT]: jsyn/src/test/java/com/synauson/jsyn/it/DtmfEventsIT.java
[WebRtcParticipantIT]: jsyn/src/test/java/com/synauson/jsyn/it/WebRtcParticipantIT.java
[WebRtcMediaE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/WebRtcMediaE2eIT.java
[WebRtcNativeParticipantE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/WebRtcNativeParticipantE2eIT.java
[WebRtcOptionsE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/WebRtcOptionsE2eIT.java
[WebRtcSttE2eIT]: jsyn/src/test/java/com/synauson/jsyn/it/WebRtcSttE2eIT.java
[SipRtpPeer]: jsyn/src/test/java/com/synauson/jsyn/it/support/SipRtpPeer.java
[WebRtcBrowserPeer]: jsyn/src/test/java/com/synauson/jsyn/it/support/WebRtcBrowserPeer.java

## Complete applications

The tests show one feature at a time. For whole applications you can run and adapt, see
[synauson/examples](https://github.com/synauson/examples):

| Example | What it shows |
|---|---|
| [WebRTC testbed](https://github.com/synauson/examples/tree/main/java/jsyn-webrtc-testbed) | A Spring Boot and React app, published as a container. Browsers join a room, audio is routed through jsyn, and VAD and turn detection events stream live to the page. It is the reference for WebRTC signalling, one conference per room, and event fan-out. |
| [Licensing tour](https://github.com/synauson/examples/tree/main/java/jsyn-licensing) | License keys and files, capabilities, air-gapped hosts, and what happens at a usage limit |
| [Windows quickstart](https://github.com/synauson/examples/tree/main/java/jsyn-windows-quickstart) | A minimal Gradle project for Windows: GStreamer setup, then file playback, native audio I/O and VAD |

## Configuration and logging

Set runtime options on `JSynConfig.builder()`; its javadoc lists every option with its
default. The STT and TTS options:

| Option | Default | Effect |
|---|---|---|
| `sttCapacity(workers, threads, maxStreams)` | measured at startup | The STT pool's workers, ONNX Runtime threads per worker, and stream cap; `maxStreams` 0 turns STT off. Each `null` keeps the measured value. |
| `cpuBudget(Double)` | detected | Cores this runtime may use, fractional allowed. Detected as the smallest of the process's cgroup CPU quota, cpuset, affinity mask and CPU count, so a container or CPU-limited service sizes its model pools to its quota; set it to share a host between runtimes without quotas. `capabilities().resources` reports what was used and why |
| `memoryBudget(Long)` | the cgroup's limit | Bytes of memory this runtime may use; STT sizes its workers within it |
| `recalibrate(boolean)` | `false` | Time the models again at startup and replace the timings cached in `calibration.json` in the state directory. A restart otherwise reuses them on the same model, CPU, CPU budget and ONNX Runtime version. Set it after changing hardware in place or to take fresh timings on an idle host. `capabilities().calibration` and `stt.source` report where each timing came from. Natives that predate the cache ignore it. |
| `sttTurnFlush(Boolean)` | off | Close each turn's transcript on a forecast of its last words as soon as turn detection ends the turn, rather than waiting for the transcription to get there. On a Ryzen 7 3700X it closed long turns about 130 ms sooner for about 26% more CPU, and the decoding it sets aside lowers the STT stream cap by about a quarter. `capabilities().stt.turnFlush` and `forecastReserve` report it. Natives that predate it ignore it. |
| `ttsCapacity(workers, threads, maxStreams)` | from the CPU count | The TTS pool's workers, ONNX Runtime threads per worker, and the cap on utterances synthesised at once; `maxStreams` 0 turns TTS off. By default a quarter of the logical CPUs go to TTS, with 2 threads a worker when that is two or more, 1 to 4 workers and 2 utterances a worker. The cap counts utterances while they play, not speakers. `capabilities().tts` reports it. |

The engine also reads these environment variables:

| Variable | Effect |
|---|---|
| `SYNAUSON_LICENSE_KEY` | The license key, used when `licenseKey` is null |
| `SYNAUSON_MODEL_STORE` | The model store, used when `modelStore` is null. Default: `~/.cache/synauson/models` (or `$XDG_CACHE_HOME/synauson/models`), `%LOCALAPPDATA%\synauson\models` on Windows |
| `SYNAUSON_STATE_DIR` | The state directory holding the cached license and the calibration cache (`calibration.json`), used when `stateDir` is null. Default: `~/.local/state/synauson` (or `$XDG_STATE_HOME/synauson`), `%LOCALAPPDATA%\synauson\state` on Windows |
| `SYNAUSON_LOG_LEVEL` | Engine log filter in `RUST_LOG` syntax, for example `info` or `debug`. Falls back to `RUST_LOG`, then `warn`. |
| `SYNAUSON_LOG_FORMAT` | `json` writes JSON lines instead of compact text |

Engine logs go to the process's stderr. The logging variables are read once, when the
natives load, so set them before the JVM starts. In containers, put the model store and
the state directory on volumes so that models, the license and the model timings survive
restarts.

## Troubleshooting

| Message or symptom | Cause and fix |
|---|---|
| `UnsatisfiedLinkError: missing native: com/synauson/jsyn/natives/<platform>/… — add jsyn-natives-<platform> to your classpath` | The natives jar for this OS is not on the runtime classpath. Add `jsyn-natives-linux` or `jsyn-natives-windows`. |
| `UnsatisfiedLinkError: jsyn does not yet support OS '…'` (or `arch`) | Only Linux and Windows on x86_64 are supported. |
| `UnsatisfiedLinkError: …onnxruntime.dll: Can't find dependent libraries` (or the same for `synauson_jni.dll`) on Windows | The Visual C++ runtime is missing: install [`vc_redist.x64.exe`](https://aka.ms/vc14/vc_redist.x64.exe). For `synauson_jni.dll`, GStreamer's `bin` folder may also be missing from `Path`. See [installation troubleshooting](docs/install.md#troubleshooting). |
| `UnsatisfiedLinkError` naming a `libgst…` library, or `gstreamer-1.0-0.dll` on Windows | GStreamer is not installed or not on the library path. See [Requirements](#requirements). On Windows, sign out and back in after changing `Path`. |
| `InternalException: GStreamer sanity check failed: required GStreamer element '…' not found` | A plugin set is missing. `errorignore` comes from `gstreamer1.0-plugins-bad`, and the mixer and codecs from `-base` and `-good`. |
| Adding a SIP participant throws `InternalException` naming `dtmfdetect` | GStreamer lacks its spandsp plugin, as on RHEL. Use a [supported distribution](docs/install.md#supported-distributions). |
| WebRTC participants fail while SIP and file participants work | The ICE plugin is missing (`gstreamer1.0-nice`), or GStreamer is 1.28. |
| `InvalidArgumentException: no license key configured: set SYNAUSON_LICENSE_KEY …` | Set the variable, or pass `licenseKey(...)`. |
| `PermissionDeniedException: license.synauson.com refused this license key: …` | The key is wrong, suspended or revoked. An expired license is not refused: see [Licensing and models](#licensing-and-models). |
| `FailedPreconditionException: ILLEGAL_LICENSE: … is not a valid synauson license: …` from `new JSyn(...)` | The license itself doesn't fit this engine (the message lists every problem, for example a missing session limit or an entitlement set that is no plan). No setting fixes it: send the message to Synauson for a corrected license. |
| `LimitExceededException: concurrent AI session limit reached: …` | Every AI session the license allows, and its burst, is in use. Remove a participant with detectors, or ask Synauson for more sessions. `capabilities().sessions` shows the limit, use and peak. |
| `InvalidArgumentException: turn_detection needs vad on the same participant: …` (or `stt needs turn_detection …`) | Each detector needs the one before it on the same participant. Add the `VadConfig` (or `TurnDetectionConfig`) it names. Older natives accepted turn detection without VAD, which then never ran. |
| `FailedPreconditionException: model 'sentito-1' is not installed: …` | The model hasn't downloaded yet, or the host is offline. Wait until `capabilities().models` reports it ready, or run `JSyn.importModels`. |
| First `new JSyn(...)` on Windows takes tens of seconds | GStreamer is building its plugin registry. Run `gst-inspect-1.0.exe coreelements` once per user. |

For more detail, rerun with `SYNAUSON_LOG_LEVEL=debug`.

## Versions

`jsyn` and the `jsyn-natives-*` jars are versioned separately: the natives are built from
the engine, and jsyn is released from this repository. Each jsyn release needs natives at
or above the `jsynNativesVersion` in that release's
[`gradle.properties`](gradle.properties), and CI tests it against exactly that version.
A natives release can come without a jsyn release when only the engine changed, so use
the newest natives that CI tests with your jsyn version. For jsyn 1.5.0 that is natives
1.5.1.

Every push to `main` publishes a snapshot of the next minor version to
`https://maven.synauson.com/snapshots`. Tags `v*` publish releases. In the javadoc,
`@since` names the release that added each API.

## Building and testing jsyn

```bash
./gradlew :jsyn:compileJava :jsyn:compileTestJava :jsyn:checkReadmeSnippets  # no natives or GStreamer needed
./gradlew :jsyn:test --tests '*Test'  # unit tests; pure Java, no license needed
```

The integration tests (`*IT`, run with `./gradlew :jsyn:test`) drive the real engine.
They need GStreamer, `SYNAUSON_LICENSE_KEY`, Playwright's Chromium
(`./gradlew :jsyn:installPlaywrightBrowsers`), and model and speech fixtures from the
engine's private repository, passed with `-DsynausonRepoDir=<dir>`. The runner expects
`models/sentito-1.onnx` and `models/fermata-1.onnx` (each with its `<id>-NOTICE.txt`), and tests that need
`synauson-server/tests/fixtures/short_speech.wav` skip without it. Outside the Synauson
team, rely on CI, which runs the full suite on Linux and Windows for every push.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

The models the engine downloads and the third-party code in the natives jars have their own
licenses, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
