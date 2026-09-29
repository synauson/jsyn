# jsyn — Java client for synauson

`jsyn` is an **in-process** Java binding to the [synauson](https://synauson.com) audio media server.
The synauson Rust runtime — GStreamer pipelines, ONNX Runtime detectors, participant graph, and
audio router — runs inside your JVM via JNI. No separate process to manage, no gRPC traffic on
the loopback.

For the gRPC consumption model (remote `synauson` server, language-agnostic clients), see the
[synauson API documentation](https://synauson.com/docs/api).

---

## Artifacts

| Maven coordinate | What it is | Required on |
|---|---|---|
| `com.synauson:jsyn:<version>` | Pure-Java API — `JSyn`, `Conference`, participant handles, event streams | Always |
| `com.synauson:jsyn-natives-linux:<version>` | `libsynauson_jni.so` + `libonnxruntime.so.1.24.4`, x86\_64 | Linux runtime |
| `com.synauson:jsyn-natives-windows:<version>` | `synauson_jni.dll` + `onnxruntime.dll`, x86\_64 | Windows runtime |

Add the pure-Java module plus the native module(s) for the platforms you ship on. Both `linux` and
`windows` natives can be on the classpath simultaneously — `NativeLoader` picks the right one at
JVM startup.

All modules are published to the public Synauson Maven repository. No credentials are needed
to download them; running Synauson needs a license key (`JSynConfig.licenseKey`).

| Repository | URL |
|---|---|
| Releases | `https://maven.synauson.com/releases` |
| Snapshots (`*-SNAPSHOT`, rebuilt from `main`) | `https://maven.synauson.com/snapshots` |

### Gradle

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://maven.synauson.com/releases") }
    // Only if you use -SNAPSHOT versions:
    // maven { url = uri("https://maven.synauson.com/snapshots") }
}

dependencies {
    implementation("com.synauson:jsyn:VERSION")
    runtimeOnly("com.synauson:jsyn-natives-linux:VERSION")   // Linux
    runtimeOnly("com.synauson:jsyn-natives-windows:VERSION") // Windows
}
```

### Maven

```xml
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
    <version>VERSION</version>
  </dependency>
  <dependency>
    <groupId>com.synauson</groupId>
    <artifactId>jsyn-natives-linux</artifactId>
    <version>VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

JDK 11+ required.

---

## Runtime prerequisites

### Linux (x86\_64)

Install GStreamer 1.26.x and its plugin sets:

```bash
# Debian / Ubuntu
sudo apt install -y libgstreamer1.0-0 gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
    gstreamer1.0-plugins-ugly libnice10

# Fedora / RHEL
sudo dnf install -y gstreamer1 gstreamer1-plugins-base \
    gstreamer1-plugins-good gstreamer1-plugins-bad-free \
    gstreamer1-plugins-ugly libnice

# Arch
sudo pacman -S gstreamer gst-plugins-base gst-plugins-good \
    gst-plugins-bad gst-plugins-ugly libnice
```

ONNX Runtime is bundled inside `jsyn-natives-linux.jar` — nothing extra to install.

### Windows (x86\_64)

Install GStreamer 1.26.7 MSVC **system-wide**. ONNX Runtime is bundled in the JAR.

1. Download the MSVC installer:
   <https://gstreamer.freedesktop.org/data/pkg/windows/1.26.7/msvc/gstreamer-1.0-msvc-x86_64-1.26.7.msi>
2. Install with **Complete** profile to the default location (`C:\gstreamer\1.0\msvc_x86_64`).
3. Set the following machine-wide environment variables (elevated PowerShell):
   ```powershell
   [Environment]::SetEnvironmentVariable(
       "GSTREAMER_1_0_ROOT_MSVC_X86_64",
       "C:\gstreamer\1.0\msvc_x86_64",
       "Machine")
   $p = [Environment]::GetEnvironmentVariable("Path","Machine")
   if (-not $p.Contains("C:\gstreamer\1.0\msvc_x86_64\bin")) {
       [Environment]::SetEnvironmentVariable(
           "Path", "$p;C:\gstreamer\1.0\msvc_x86_64\bin", "Machine")
   }
   ```
4. Reboot or sign out/in to refresh PATH for new processes.
5. Once per Windows user account, build the GStreamer plugin registry: `gst-inspect-1.0.exe coreelements`.
   The first GStreamer init on a machine scans every installed plugin, which took 7–44 s on fresh CI
   runners; skip this and the scan happens inside the first `new JSyn(...)`.

Verify with: `gst-launch-1.0.exe --version` from a fresh PowerShell prompt.

**Minimum version:** GStreamer 1.24+. GStreamer 1.22.x and older will not work.

---

## License key

jsyn runs under a license key from Synauson (free-tier keys included). Pass it with
`JSynConfig.builder().licenseKey(...)`, or set `SYNAUSON_LICENSE_KEY` in the environment.
At startup the runtime exchanges it at `license.synauson.com` for a signed license file,
caches it in the state directory (`JSynConfig.stateDir`, default `$SYNAUSON_STATE_DIR` or
the per-user state directory) and renews it about once a day. The license lists the AI
capabilities you may use (for example `FEATURE_VAD`, `FEATURE_TURN_DETECTION`) and your
usage limits.

- No key, or a key the licensing server refuses: `new JSyn(...)` throws.
- The licensing server can't be reached: the runtime starts with the cached license while
  it's valid (otherwise with free-tier limits) and upgrades when the server answers.
- A capability your license doesn't include: adding a participant with that detector throws
  `PermissionDeniedException` naming the capability.
- Over a usage limit: the new conference or detector throws `LimitExceededException`
  naming the limit. Nothing already running is ever stopped.

`jsyn.capabilities()` reports the license, its limits and current usage, and each model's
state.

## Models

Detectors load their ONNX models from a **model store**, laid out as
`<model-id>/<version>/<file>`:

```
<model-store>/
├── silero-vad/5/silero_vad.onnx            (Voice Activity Detection)
└── smart-turn/3.2-cpu/smart_turn_v3.onnx   (end-of-turn detection)
```

At startup the runtime downloads the models your license includes into the store, in the
background, and checks each file against the size, SHA-256 and signature this release pins.
The store is `JSynConfig.modelStore` if set, else `$SYNAUSON_MODEL_STORE`, else the per-user
cache (`%LOCALAPPDATA%\synauson\models` on Windows, `~/.cache/synauson/models` on Linux). A
participant that asks for a detector whose model hasn't arrived yet throws
`FailedPreconditionException` naming the model.

For hosts without internet access, fill a store from a folder of the `.onnx` files instead
(`offline(true)` plus a `licenseFile` from Synauson):

```java
JSyn.importModels(Path.of("/path/to/models"), Path.of("/opt/synauson/models"));
```

---

## Quick start

```java
import com.synauson.jsyn.*;
import com.synauson.jsyn.participant.*;

public class JsynHello {
    public static void main(String[] args) throws Exception {
        JSynConfig config = JSynConfig.builder()
            .licenseKey(System.getenv("SYNAUSON_LICENSE_KEY"))
            .modelStore("/opt/synauson/models")  // or "C:\\synauson\\models"
            .maxConferences(100)
            .build();

        try (JSyn jsyn = new JSyn(config)) {
            try (Conference conf = jsyn.startConference("call-12345")) {

                // Add a file-playback participant
                FileParticipantHandle file = conf.addFileParticipant(
                    FileParticipantSpec.builder()
                        .participantId("hold-music")
                        .filePath("/audio/hold.wav")
                        .audioFormat(NativeAudioFormat.PCM_S16LE16K_MONO)
                        .build());

                // Subscribe to VAD events from another participant
                Subscription sub = conf.streamVadEvents("agent", event ->
                    System.out.println("VAD: " + event.state()));

                // ... do work ...

                sub.cancel();
            }
        }
    }
}
```

`try-with-resources` is important: dropping a `Conference` or `JSyn` without `close()` leaks
native pipelines. The Rust runtime shuts down only when the `JSyn` instance closes.

### One JSyn per JVM

GStreamer and ONNX Runtime are process-global singletons. Construct at most **one** `JSyn`
instance per JVM process; create it once at startup and share it across your application.

---

## Participant types

| Spec class | What it does | Typical use |
|---|---|---|
| `FileParticipantSpec` | Plays a WAV / OGG / MP3 file into the conference, or records all participants to disk | Hold music, IVR prompts, full-conference recording |
| `RecordingParticipantSpec` | Records the conference mix to disk | Compliance recording |
| `SipParticipantSpec` | SIP leg (RTP) whose peer media is already known | Inbound carrier calls, softphone callers |
| `SipReservationSpec` + `SipConnectionSpec` | Two-phase SIP leg: reserve our ports for the SDP offer, connect with the answer's `SipRemoteMedia` | Outbound calls |
| `WebRtcParticipantSpec` | WebRTC peer (SDP offer/answer, ICE) | Browser callers |
| `NativeParticipant` | In-process bidirectional audio via `ByteBuffer` rings | Custom Java audio sources/sinks |

For an outbound call, `conf.reserveSipParticipant(...)` binds the RTP/RTCP ports for your SDP
offer and starts nothing; after the answer, `conf.connectSipParticipant(...)` starts the
participant on those ports and returns the usual `SipParticipantHandle`. `removeParticipant`
releases a reservation that never connects.

```java
SipReservation r = conf.reserveSipParticipant(
    SipReservationSpec.builder().participantId("callee").build());
// ... send an INVITE whose SDP offer carries r.localRtpPort(), await the answer ...
SipParticipantHandle callee = conf.connectSipParticipant(SipConnectionSpec.builder()
    .participantId("callee")
    .remote(SipRemoteMedia.builder()
        .remoteIp(answerIp).remoteRtpPort(answerPort).codec("PCMU").dtmfPayloadType(101)
        .build())
    .build());
```

A WebRTC participant can set its own ICE port range, STUN and TURN servers, relay-only policy,
jitter buffer and Opus encoding. Anything it leaves unset takes the runtime default from
`JSynConfig` (`webrtcStunServer`, `webrtcJitterBufferMs`, `webrtcIcePortRange`). Unless the call
is relayed through TURN, its media flows on a UDP port inside the ICE port range, so that range
is what a firewall or container has to allow. `handle.stats().effectiveOptions` reports the
options the participant runs with.

```java
WebRtcParticipantHandle caller = conf.addWebRtcParticipant(WebRtcParticipantSpec.builder()
    .participantId("caller")
    .sdpOffer(browserOffer)
    .icePortRange(40000, 40099)
    .turnServers(List.of("turn://user:password@turn.example.com:3478?transport=udp"))
    .opusBitrate(32_000)
    .build());
```

Streaming subscriptions are available for VAD events, SmartTurn events, File end-of-stream events,
DTMF events (SIP/WebRTC), and ICE candidates (WebRTC). Each subscription returns a `Subscription`
handle — call `cancel()` to stop.

---

## How native loading works

`NativeLoader` extracts the platform `.so`/`.dll` and ONNX Runtime from the `jsyn-natives-<platform>`
JAR into a temp directory under `java.io.tmpdir`, pre-loads ORT, then loads the JNI library. A JVM
shutdown hook deletes the temp directory on exit.

GStreamer plugins on Windows are discovered via the `GSTREAMER_1_0_ROOT_MSVC_X86_64` environment
variable (the system install). On Linux they are found via the standard GStreamer plugin path from
the system install.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `UnsatisfiedLinkError: missing native: com/synauson/jsyn/natives/...` | `jsyn-natives-<platform>` not on classpath | Add the `runtimeOnly` dependency for your OS |
| `Can't find gstreamer-1.0-0.dll` (Windows) | GStreamer not installed or PATH not refreshed | Install per the Windows section above; reboot |
| `FailedPreconditionException: model 'silero-vad' version 5 is not installed` (or `failed verification`) | The store lacks that model, or its file doesn't match the pinned SHA-256 | Run `JSyn.importModels(<folder with the .onnx files>, <store>)` and pass the same store to `modelStore` |
| First `new JSyn(...)` on Windows takes tens of seconds | GStreamer is building its plugin registry | Run `gst-inspect-1.0.exe coreelements` once after install (Windows step 5) |
| `UnsatisfiedLinkError: msvcr100.dll missing` | Old MSVC runtime missing | Install Visual C++ Redistributable for VS 2015–2022 |

For deeper diagnostics, run with `-Djsyn.log=trace` to enable native-side tracing output on stderr.

---

## Versioning

jsyn version numbers match the synauson server release they were built with. Always use matching
versions — the JNI ABI carries no stability contract across versions. Mixing `jsyn` and
`jsyn-natives-<platform>` at different versions causes immediate `UnsatisfiedLinkError` or
undefined behavior.

Snapshot versions (`*-SNAPSHOT`) are published on every push to `main`. Tagged releases (`v0.1.0`,
etc.) are published on version tags.

---

## Examples

The [synauson/examples](https://github.com/synauson/examples) repository contains complete,
runnable reference applications built with jsyn.

---

## Building from source

```bash
git clone https://github.com/synauson/jsyn
cd jsyn
./gradlew :jsyn:compileJava
```

Running integration tests requires the native artifacts (`jsyn-natives-linux` or
`jsyn-natives-windows`), which Gradle downloads from `https://maven.synauson.com/releases`:

```bash
./gradlew :jsyn:test
```

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
