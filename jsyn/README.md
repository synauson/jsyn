# jsyn — Java client for synauson

`jsyn` is an **in-process** Java binding to the synauson audio media server.
The synauson Rust runtime (GStreamer pipelines + ONNX Runtime detectors + the
participant + routing graph) runs inside your JVM via JNI; there is no
separate process to manage and no gRPC traffic on the loopback.

For the gRPC consumption model (separate `synauson` server, remote clients),
see [`docs/api.md`](../../docs/api.md) and [`docs/deployment.md`](../../docs/deployment.md).

---

## Modules

| Maven coordinate | What it is | Required on |
|---|---|---|
| `com.synauson:jsyn:<version>` | Pure-Java API (JSyn, Conference, participant handles, event streams) | Always |
| `com.synauson:jsyn-natives-linux:<version>` | `libsynauson_jni.so` + `libonnxruntime.so.1.24.4`, x86_64 | Linux runtime |
| `com.synauson:jsyn-natives-windows:<version>` | `synauson_jni.dll` + `onnxruntime.dll`, x86_64 | Windows runtime |
| `com.synauson:jsyn-proto:<version>` *(planned, J1.10.4)* | Generated Java stubs for `synauson.v1.Synauson` gRPC API | Only if you also talk to a remote synauson server |

Add the pure-Java module plus the natives module(s) for the platforms you ship
on. Both `linux` and `windows` natives can be on the classpath simultaneously —
`NativeLoader` picks the right one at JVM startup from `os.name` / `os.arch`.

All modules are published to your internal Nexus
(`com.synauson` group, `0.1.0-SNAPSHOT` while pre-1.0).

### Gradle

```kotlin
repositories {
    maven { url = uri("https://nexus.benashby.com/repository/maven-public/") }
}

dependencies {
    implementation("com.synauson:jsyn:0.1.0-SNAPSHOT")
    runtimeOnly("com.synauson:jsyn-natives-linux:0.1.0-SNAPSHOT")
    runtimeOnly("com.synauson:jsyn-natives-windows:0.1.0-SNAPSHOT")
}
```

### Maven

```xml
<dependencies>
  <dependency>
    <groupId>com.synauson</groupId>
    <artifactId>jsyn</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.synauson</groupId>
    <artifactId>jsyn-natives-windows</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

JDK 11+ is required (`build.gradle.kts` pins a JavaLanguageVersion 11
toolchain).

---

## Runtime prerequisites

### Linux (x86_64)

Install GStreamer 1.26.7 and its plugins:

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

ONNX Runtime is bundled inside `jsyn-natives-linux.jar`; nothing to install.

### Windows (x86_64)

You need GStreamer 1.26.7 MSVC installed **system-wide**. ONNX Runtime is
bundled in the JAR.

1. Download the MSVC installer:
   <https://gstreamer.freedesktop.org/data/pkg/windows/1.26.7/msvc/gstreamer-1.0-msvc-x86_64-1.26.7.msi>
2. Install with **Complete** profile to the default location:
   ```
   C:\gstreamer\1.0\msvc_x86_64
   ```
3. Set the following machine-wide environment variables (PowerShell, elevated):
   ```powershell
   [Environment]::SetEnvironmentVariable(
       "GSTREAMER_1_0_ROOT_MSVC_X86_64",
       "C:\gstreamer\1.0\msvc_x86_64",
       "Machine")
   $p = [Environment]::GetEnvironmentVariable("Path","Machine")
   if (-not $p.Contains("C:\gstreamer\1.0\msvc_x86_64\bin")) {
       [Environment]::SetEnvironmentVariable(
           "Path",
           "$p;C:\gstreamer\1.0\msvc_x86_64\bin",
           "Machine")
   }
   ```
4. Reboot or sign out/in so the new PATH takes effect for new processes.

Verify with: `gst-launch-1.0.exe --version` from a fresh PowerShell prompt.

**Version note:** jsyn requires GStreamer 1.24+ at runtime; 1.26.7 is the
standard version. GStreamer 1.22.x and older will not work.

---

## Models

The native side needs two ONNX models on disk:

```
<models-dir>/
├── silero_vad.onnx       (VAD)
└── smart_turn.onnx       (end-of-turn detection)
```

Pass the directory path to `JSynConfig.builder().modelsDir(...)`. Both files
ship with the synauson release tarball (`models/` directory), or download
directly:

- Silero VAD: <https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx>
- Smart Turn: <https://huggingface.co/pipecat-ai/smart-turn>

(Same models the gRPC server uses — see [`docs/deployment.md`](../../docs/deployment.md).)

---

## Quick start

```java
import com.synauson.jsyn.*;
import com.synauson.jsyn.participant.*;

public class JsynHello {
    public static void main(String[] args) throws Exception {
        JSynConfig config = JSynConfig.builder()
            .modelsDir("C:\\synauson\\models")          // or "/opt/synauson/models"
            .maxConferences(100)
            .build();

        try (JSyn jsyn = new JSyn(config)) {
            try (Conference conf = jsyn.startConference("call-12345")) {

                // Add a file-playback participant
                FileParticipantHandle file = conf.addFileParticipant(
                    FileParticipantSpec.builder()
                        .participantId("hold-music")
                        .filePath("C:\\audio\\hold.wav")
                        .audioFormat(NativeAudioFormat.PCM_S16LE16K_MONO)
                        .build());

                // Subscribe to VAD events from another participant
                Subscription sub = conf.streamVadEvents("agent", event ->
                    System.out.println("VAD: " + event.state()));

                // ... do work ...

                sub.cancel();   // streaming stops cleanly
            }
        }
    }
}
```

The `try-with-resources` blocks above are important: dropping a `Conference`
or `JSyn` instance without `close()` leaks native pipelines. The Rust runtime
is shut down only when the `JSyn` instance closes.

### One JSyn per JVM

The underlying GStreamer and ONNX Runtime libraries are process-global
singletons. Construct at most **one** `JSyn` instance per JVM process; create
it once at startup and share it across your application. Constructing a second
one after closing the first works, but is uncommon — most apps treat `JSyn`
as a lifelong singleton.

---

## Participant types

| Spec class | What it does | Typical use |
|---|---|---|
| `FileParticipantSpec` | Plays a WAV / OGG / MP3 file into the conference, or records all participants to disk | Hold music, IVR prompts, full-conference recording |
| `RecordingParticipantSpec` | Records the conference mix to disk on the fly | Compliance recording |
| `SipParticipantSpec` | Inbound or outbound SIP leg (RTP) | Carrier trunks, softphone callers (M2) |
| `WebRtcParticipantSpec` | WebRTC peer (SDP offer/answer, ICE) | Browser callers (M3) |
| `NativeParticipant` | In-process bidirectional audio via `ByteBuffer` rings | Custom Java audio sources/sinks |

Streaming subscriptions are available for VAD events, SmartTurn events, File
end-of-stream events, DTMF events (SIP/WebRTC), and ICE candidates (WebRTC).
Each subscription returns a `Subscription` handle — call `cancel()` to stop.

---

## Where things live at runtime

- `NativeLoader` extracts `synauson_jni.dll` (or `.so`) and `onnxruntime.dll`
  (or `.so.1.24.4`) from the `jsyn-natives-<platform>` JAR into a temp dir
  (`jsyn-natives-XXXXXX` under `java.io.tmpdir`), pre-loads ORT, then loads
  the JNI cdylib. A JVM shutdown hook deletes the temp dir.
- GStreamer plugins on Windows are discovered via
  `GSTREAMER_1_0_ROOT_MSVC_X86_64` (the system install). On Linux they come
  from the system gstreamer install (no env var needed).
- ONNX models are loaded from the `modelsDir` you passed to `JSynConfig`.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `UnsatisfiedLinkError: missing native: com/benashby/jsyn/natives/...` | `jsyn-natives-<platform>` not on classpath | Add the runtime-only dependency for your OS |
| `Can't find gstreamer-1.0-0.dll` (Windows) | GStreamer not installed, or installed without PATH update | Install per the Windows section above; reboot to refresh PATH |
| Process exits silently on first `JSyn` construction | ORT model file missing or wrong filename | Verify `silero_vad.onnx` and `smart_turn.onnx` live directly under `modelsDir` |
| `UnsatisfiedLinkError: ... msvcr100.dll missing` | Old MSVC runtime missing | Install Visual C++ Redistributable for VS 2015–2022 |

For deeper diagnostics, run with `-Djsyn.log=trace` to see native-side
tracing-subscriber output (logged to stderr).

---

## Versioning

The jsyn module version pins to the synauson server version (single source
tree). Updates ship together. Snapshot versions (`*-SNAPSHOT`) are published
on every push to `main`; tagged releases (`0.1.0`, `0.1.1`, ...) are
published on `v*` tags via `release.yml`.

The native ABI does NOT carry a stable contract across versions — always use
the `jsyn-natives-<platform>` artifact whose version exactly matches your
`jsyn` artifact. Mixing versions across the JNI boundary causes immediate
`UnsatisfiedLinkError` or undefined behavior.

---

## License

Copyright © 2026 Ben Ashby. All Rights Reserved. Proprietary; no license is
granted to use, copy, modify, or distribute without explicit written
permission.
