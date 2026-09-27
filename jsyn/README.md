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

All modules are published to the public Synauson Maven repository
(`com.synauson` group): releases at `https://maven.synauson.com/releases`,
snapshots at `https://maven.synauson.com/snapshots`. No credentials are needed.

### Gradle

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://maven.synauson.com/snapshots") }
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

## License key

jsyn runs under a license key from Synauson (free-tier keys included). Pass it with
`JSynConfig.builder().licenseKey(...)`, or set `SYNAUSON_LICENSE_KEY`. At startup the
runtime exchanges it at `license.synauson.com` for a signed license file, caches it in the
state directory (`JSynConfig.stateDir`) and renews it about once a day. If the licensing
server can't be reached, the runtime starts with the cached license (otherwise free-tier
limits); a missing or refused key makes `new JSyn(...)` throw. A detector the license
doesn't include throws `PermissionDeniedException`; a usage limit throws
`LimitExceededException` for the new work only. `jsyn.capabilities()` reports the
license, limits, usage and model state.

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
            .modelStore("C:\\synauson\\models")          // or "/opt/synauson/models"
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
- ONNX models are loaded from the model store (`JSynConfig.modelStore`, or the default store) and checked against the SHA-256 this release pins.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `UnsatisfiedLinkError: missing native: com/synauson/jsyn/natives/...` | `jsyn-natives-<platform>` not on classpath | Add the runtime-only dependency for your OS |
| `Can't find gstreamer-1.0-0.dll` (Windows) | GStreamer not installed, or installed without PATH update | Install per the Windows section above; reboot to refresh PATH |
| `FailedPreconditionException: model 'silero-vad' version 5 is not installed` (or `failed verification`) | The store lacks that model, or its file doesn't match the pinned SHA-256 | Run `JSyn.importModels(<folder with the .onnx files>, <store>)` and pass the same store to `modelStore` |
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

Licensed under the [Apache License, Version 2.0](../LICENSE). See [NOTICE](../NOTICE).
