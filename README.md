# jsyn

[![ci](https://github.com/synauson/jsyn/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/synauson/jsyn/actions/workflows/ci.yml)

jsyn is the Java SDK for the [Synauson](https://synauson.com) media engine. It runs the
engine inside your JVM through JNI, so there is no separate server to deploy. The engine
includes GStreamer media pipelines, an audio router, and ONNX voice-activity and
end-of-turn detectors. From Java you create conferences, connect SIP, WebRTC, file and
in-process audio participants, route audio between them, and receive detector and
signalling events.

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
| Network | At startup the engine exchanges the key at `license.synauson.com` and downloads the models your license includes from `dl.synauson.com`. See [offline hosts](#licensing-and-models) if the host has no internet access. |

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
STT, and each needs the one before it on the same participant: turn detection without
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

### Voice-agent event stream (preview)

An agent that talks with a participant needs one ordered stream of what that
participant does. `conf.streamAgentEvents(id, options, observer)` delivers it as
`AgentEvent`s for any participant with `TurnDetectionConfig` (and the `VadConfig` that
drives it); without turn detection it throws `AgentStreamException` with reason
`TURN_DETECTION_REQUIRED`. It is a preview: it carries speech activity now, and turn
events (turn started, words, early and confirmed end of turn) come in later releases.

| Event | Fields | When |
|---|---|---|
| `Subscribed` | `oldestSeq`, `lastSeq`, `stt` | First on every subscription: the oldest event the stream keeps, its newest seq, whether STT runs |
| `Heartbeat` | `conferenceMs`, `sttDecodedMs`, `sttBacklogMs` | Whenever nothing else came for the heartbeat interval (default 1000 ms, `withHeartbeatMs`) |
| `SpeechStarted` | `atMs`, `probability` | VAD heard speech start. Raw voice activity: noise can start it too. |
| `SpeechStopped` | `atMs`, `speechMs` | VAD heard it stop; `speechMs` is how long it lasted |
| `Error` | `reason`, `message`, `metadata`, `turnId` | A recoverable problem; the stream goes on |
| `StreamEnded` | `reason` | Last: the participant was removed or the conference terminated. `onCompleted` follows. |
| `Unknown` | `type`, `json` | A kind from a newer engine. Ignore it, but it still has a seq. |

Every event has `conferenceId`, `participantId`, `streamId`, `seq` and
`timestampUnixMs` (wall clock). Times such as `atMs` and `conferenceMs` are conference
time: the conference's pipeline clock in ms, the same for all its participants, so you
can compare one participant's speech with another's. `atMs` is where the speech really
started or stopped, so it is earlier than the event by up to VAD's `minSpeechMs` or
`minSilenceMs`.

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

### Licensing and models

The license key comes from `JSynConfig.licenseKey`, or from `$SYNAUSON_LICENSE_KEY` when
that is null. The engine exchanges it for a signed license, caches the license in the
state directory, and renews it about once a day. If the licensing server is unreachable,
the engine starts on the cached license. A capability your license lacks throws
`PermissionDeniedException`. Going over a usage limit throws `LimitExceededException` for
the new conference or detector; nothing already running is stopped.
`jsyn.capabilities()` reports the license, its limits, current usage and the state of
each model.

Entitlements follow the detector chain: each includes the ones below it.
`FEATURE_TURN_DETECTION` also runs VAD, and `FEATURE_STT` also runs turn detection and VAD.
A participant counts one stream, for its highest detector: STT with its turn detection and
VAD counts one `FEATURE_STT` stream. A capability your license grants only through a
higher one reports `entitled` with `includedBy` naming that code, and its streams count
against that code: under a license with only `FEATURE_TURN_DETECTION`, VAD on its own
counts a `FEATURE_TURN_DETECTION` stream.

The models (sentito-1, turn detection) download into the model store in the background.
Adding a detector before its model is ready throws `FailedPreconditionException` naming
the model. On a host with no internet access, set `offline(true)` and a `licenseFile`, and
fill the store from a folder of `.onnx` files with `JSyn.importModels(from, store)`.

The [licensing tour](https://github.com/synauson/examples/tree/main/java/jsyn-licensing)
walks through all of this against a real free-tier license, including how to wait for
models and what to do when a limit is hit.

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
| Voice-agent event stream | [AgentStreamIT] | `streamAgentEvents`: `Subscribed` first, speech events in conference time, resuming from a cursor, `StreamEnded` on removal, and `TURN_DETECTION_REQUIRED` without turn detection |
| Streaming speech-to-text | [SttIT] | `SttConfig` needs `TurnDetectionConfig`, `streamTranscriptEvents` needs STT on the participant, and `capabilities().stt`. Transcript content is tested on the engine side. |
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
default. The STT options:

| Option | Default | Effect |
|---|---|---|
| `sttCapacity(workers, threads, maxStreams)` | measured at startup | The STT pool's workers, ONNX Runtime threads per worker, and stream cap; `maxStreams` 0 turns STT off. Each `null` keeps the measured value. |
| `sttTurnFlush(Boolean)` | off | Close each turn's transcript on a forecast of its last words as soon as turn detection ends the turn, rather than waiting for the transcription to get there. On a Ryzen 7 3700X it closed long turns about 130 ms sooner for about 26% more CPU, and the decoding it sets aside lowers the STT stream cap by about a quarter. `capabilities().stt.turnFlush` and `forecastReserve` report it. Natives that predate it ignore it. |

The engine also reads these environment variables:

| Variable | Effect |
|---|---|
| `SYNAUSON_LICENSE_KEY` | The license key, used when `licenseKey` is null |
| `SYNAUSON_MODEL_STORE` | The model store, used when `modelStore` is null. Default: `~/.cache/synauson/models` (or `$XDG_CACHE_HOME/synauson/models`), `%LOCALAPPDATA%\synauson\models` on Windows |
| `SYNAUSON_STATE_DIR` | The state directory holding the cached license, used when `stateDir` is null. Default: `~/.local/state/synauson` (or `$XDG_STATE_HOME/synauson`), `%LOCALAPPDATA%\synauson\state` on Windows |
| `SYNAUSON_LOG_LEVEL` | Engine log filter in `RUST_LOG` syntax, for example `info` or `debug`. Falls back to `RUST_LOG`, then `warn`. |
| `SYNAUSON_LOG_FORMAT` | `json` writes JSON lines instead of compact text |

Engine logs go to the process's stderr. The logging variables are read once, when the
natives load, so set them before the JVM starts. In containers, put the model store and
the state directory on volumes so that models and the license survive restarts.

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
| `PermissionDeniedException: license.synauson.com refused this license key: …` | The key is wrong, expired or revoked. |
| `InvalidArgumentException: turn_detection needs vad on the same participant: …` (or `stt needs turn_detection …`) | Each detector needs the one before it on the same participant. Add the `VadConfig` (or `TurnDetectionConfig`) it names. Older natives accepted turn detection without VAD, which then never ran. |
| `FailedPreconditionException: model 'sentito-1' version 5 is not installed: …` | The model hasn't downloaded yet, or the host is offline. Wait until `capabilities().models` reports it ready, or run `JSyn.importModels`. |
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
`models/sentito-1.onnx` and `models/fermata-1.onnx`, and tests that need
`synauson-server/tests/fixtures/short_speech.wav` skip without it. Outside the Synauson
team, rely on CI, which runs the full suite on Linux and Windows for every push.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
