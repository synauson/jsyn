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

All modules are published to the synauson Nexus registry. Credentials are provided with your
synauson license.

### Gradle

```kotlin
repositories {
    maven {
        url = uri("https://nexus.benashby.com/repository/maven-public/")
        credentials {
            username = System.getenv("NEXUS_USER")
            password = System.getenv("NEXUS_PASSWORD")
        }
    }
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
    <id>synauson-nexus</id>
    <url>https://nexus.benashby.com/repository/maven-public/</url>
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

Verify with: `gst-launch-1.0.exe --version` from a fresh PowerShell prompt.

**Minimum version:** GStreamer 1.24+. GStreamer 1.22.x and older will not work.

---

## Models

The native runtime needs two ONNX models on disk:

```
<models-dir>/
├── silero_vad.onnx       (Voice Activity Detection)
└── smart_turn.onnx       (end-of-turn detection)
```

Pass the directory path to `JSynConfig.builder().modelsDir(...)`. Both files ship with the
synauson release tarball (`models/` directory), or download directly:

- Silero VAD: <https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx>
- Smart Turn: <https://huggingface.co/pipecat-ai/smart-turn>

---

## Quick start

```java
import com.synauson.jsyn.*;
import com.synauson.jsyn.participant.*;

public class JsynHello {
    public static void main(String[] args) throws Exception {
        JSynConfig config = JSynConfig.builder()
            .modelsDir("/opt/synauson/models")  // or "C:\\synauson\\models"
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
| `SipParticipantSpec` | Inbound or outbound SIP leg (RTP) | Carrier trunks, softphone callers |
| `WebRtcParticipantSpec` | WebRTC peer (SDP offer/answer, ICE) | Browser callers |
| `NativeParticipant` | In-process bidirectional audio via `ByteBuffer` rings | Custom Java audio sources/sinks |

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
| `UnsatisfiedLinkError: missing native: com/benashby/jsyn/natives/...` | `jsyn-natives-<platform>` not on classpath | Add the `runtimeOnly` dependency for your OS |
| `Can't find gstreamer-1.0-0.dll` (Windows) | GStreamer not installed or PATH not refreshed | Install per the Windows section above; reboot |
| Process exits silently on first `JSyn` construction | ORT model file missing or wrong filename | Verify `silero_vad.onnx` and `smart_turn.onnx` are directly under `modelsDir` |
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
`jsyn-natives-windows`) from the synauson Nexus registry. Set `NEXUS_USER` and `NEXUS_PASSWORD`
in your environment or in `~/.gradle/gradle.properties`, then:

```bash
./gradlew :jsyn:test
```

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
