# jsyn

jsyn is the Java SDK for [Synauson](https://synauson.com), a media engine for voice
calls with speech AI built in. It runs the engine inside your JVM, so there is no server
to deploy. From Java you start conferences, connect SIP, WebRTC, file and in-process
audio participants, route audio between them, and get voice activity, end of turn,
speech to text and text to speech on any participant.

jsyn 1.6.0 needs jsyn-natives 1.7.1 or newer. Coming from 1.5? Read
[Upgrading to 1.6](docs/upgrading.md) first: it needs a new license key.

## Contents

- [Requirements](#requirements)
- [Install](#install)
- [Quickstart](#quickstart)
- [Guides](#guides)
- [Examples](#examples)
- [Versions](#versions)
- [Getting help](#getting-help)
- [License](#license)

## Requirements

| | |
|---|---|
| Java | 11 or newer |
| Operating system | Linux x86_64 with glibc 2.34 or newer, or Windows 10 or 11 x86_64. macOS and ARM are not supported. |
| GStreamer | 1.24 or 1.26, installed on the machine. 1.28 is not supported, because it breaks WebRTC. On Linux that rules distributions in or out: see [supported distributions](docs/install.md#supported-distributions). |
| Visual C++ runtime | Windows only: the Microsoft Visual C++ Redistributable, x64. |
| License key | Required. Synauson issues keys, free ones included, under the [End User License Agreement](https://synauson.com/legal/eula/). |
| Network | HTTPS to `usage.synauson.com`, `license.synauson.com`, `status.synauson.com` and Cloudflare R2 (`*.r2.cloudflarestorage.com`), and a synchronised clock (NTP), unless you [run air-gapped](docs/licensing-and-models.md#run-without-internet-access). See [Network and firewall](docs/install.md#network-and-firewall). |

ONNX Runtime ships inside the natives jar and inference runs on the CPU, so there is
nothing to install for inference. [Installing the runtime](docs/install.md) has the steps for Linux and
Windows, the firewall rules and a check that everything is in place.

## Install

jsyn is a Java API jar plus one natives jar per platform, all from the Synauson Maven
repository, which needs no credentials. Put both natives jars on the classpath if you
ship to both platforms: jsyn loads the one for the running OS.

| Artifact | Contents |
|---|---|
| `com.synauson:jsyn` | The Java API |
| `com.synauson:jsyn-natives-linux` | The engine and ONNX Runtime for Linux x86_64 |
| `com.synauson:jsyn-natives-windows` | The engine and ONNX Runtime for Windows x86_64 |

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://maven.synauson.com/releases") }
}

val jsynVersion = "1.6.0"
val jsynNativesVersion = "1.7.1"

dependencies {
    implementation("com.synauson:jsyn:$jsynVersion")
    runtimeOnly("com.synauson:jsyn-natives-linux:$jsynNativesVersion")
    runtimeOnly("com.synauson:jsyn-natives-windows:$jsynNativesVersion")
}
```

Maven:

```xml
<properties>
  <jsyn.version>1.6.0</jsyn.version>
  <jsyn.natives.version>1.7.1</jsyn.natives.version>
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
  <dependency>
    <groupId>com.synauson</groupId>
    <artifactId>jsyn-natives-windows</artifactId>
    <version>${jsyn.natives.version}</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

jsyn itself depends only on Gson and the JSpecify annotations.

## Quickstart

This program plays a WAV file into a conference and prints the playback events. Set
`SYNAUSON_LICENSE_KEY`, then run it with the path to a WAV file:

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

It prints:

```text
PlaybackStarted
Eos
```

The engine logs warnings to stderr as well. On the first run it starts downloading the
models in the background, and a short program like this one may
exit before they finish; that is harmless.

Next, read [Conferences and participants](docs/conferences.md) for how the pieces fit
together, then the guide for what you are building.

## Guides

| Guide | What it covers |
|---|---|
| [Installing the runtime](docs/install.md) | GStreamer on Linux and Windows, the Visual C++ runtime, firewall rules, NVIDIA GPUs |
| [Licensing and models](docs/licensing-and-models.md) | License keys, plans and AI sessions, license states, model downloads, the model store, offline hosts |
| [Conferences and participants](docs/conferences.md) | The runtime, conferences, routing audio, file playback, recording, mute and announcements, threads and closing |
| [SIP calls](docs/sip.md) | Inbound and outbound RTP legs, codecs, SRTP, DTMF |
| [WebRTC calls](docs/webrtc.md) | Offer and answer, trickle ICE, STUN and TURN, per-call options |
| [Native participants](docs/native-participants.md) | Sending and receiving raw audio from Java |
| [Voice activity and turn detection](docs/detection.md) | VAD and end-of-turn events on any participant |
| [Speech to text](docs/speech-to-text.md) | Streaming transcripts, words and turns |
| [Voice agents](docs/voice-agents.md) | The agent event stream: turns, words, eager end of turn, resuming after a lag |
| [Text to speech](docs/text-to-speech.md) | Speaking into a call, voices, interrupting, playback events |
| [Configuration and logging](docs/configuration.md) | Every runtime option, environment variables, logs, containers |
| [Capacity and sizing](docs/sizing.md) | How many calls a machine carries, and how the engine measures it |
| [Errors](docs/errors.md) | The exception classes and the reason codes |
| [Troubleshooting](docs/troubleshooting.md) | Messages you may see, their cause and the fix |
| [Upgrading to 1.6](docs/upgrading.md) | What changed from jsyn 1.5 and what to do about it |

The API reference is the javadoc, published with every release as
`jsyn-<version>-javadoc.jar`. Most IDEs attach it on their own.

## Examples

To see jsyn working end to end, start with
[synauson/examples](https://github.com/synauson/examples): complete applications built
on the released jsyn that you can run and adapt.

| Example | What it shows |
|---|---|
| [Windows quickstart](https://github.com/synauson/examples/tree/main/java/jsyn-windows-quickstart) | A minimal Gradle project on Windows: file playback, native audio and VAD |
| [Voice agent](https://github.com/synauson/examples/tree/main/java/jsyn-voice-agent) | Speech to text, text to speech, and an agent that answers a caller and stops when interrupted |
| [Licensing](https://github.com/synauson/examples/tree/main/java/jsyn-licensing) | License keys and files, the capabilities report, offline hosts and session limits, checked by a tour |
| [WebRTC testbed](https://github.com/synauson/examples/tree/main/java/jsyn-webrtc-testbed) | A Spring Boot and React app where browsers join a room and see VAD and turn events live |

## Versions

jsyn and the `jsyn-natives-*` jars have separate versions. The natives are the engine,
and an engine fix can ship as a new natives release with no new jsyn. Each jsyn release
names the natives version it needs in [CHANGELOG.md](CHANGELOG.md); use that version or
a newer one. For jsyn 1.6.0 that is 1.7.1. The changelog also lists what each release
changed, and `@since` in the javadoc names the release that added each API.

Every file in `https://maven.synauson.com/releases` has `.sha256` and `.sha512`
checksums beside it. Each jsyn release's jar, sources, javadoc, POM and module file also
carry a signed build provenance attestation from the workflow that built them from the
release tag, and each GitHub Release is immutable, so its tag can never move. With the
[GitHub CLI](https://cli.github.com/):

```bash
gh attestation verify jsyn-<version>.jar -R synauson/jsyn
gh release verify v<version> -R synauson/jsyn
```

The `jsyn-natives-*` jars are signed with synauson's key, published at
https://synauson.com/cosign.pub. Each jar has a `.sigstore.json` bundle beside it in the
Maven repository; Gradle and Maven ignore it. The same key signs the server image
`cr.synauson.com/synauson/synauson-server`. With [cosign](https://docs.sigstore.dev/cosign/system_config/installation/):

```bash
cosign verify-blob --key https://synauson.com/cosign.pub --insecure-ignore-tlog=true \
  --bundle jsyn-natives-linux-<natives version>.jar.sigstore.json \
  jsyn-natives-linux-<natives version>.jar
cosign verify --key https://synauson.com/cosign.pub --insecure-ignore-tlog=true \
  cr.synauson.com/synauson/synauson-server:<natives version>
```

These signatures are not recorded in Sigstore's public transparency log, which is why
`--insecure-ignore-tlog=true` is needed; the key is what proves them.

## Getting help

Contact Synauson through the contact you received with your license key.
[SUPPORT.md](SUPPORT.md) says what to include in a report, and
[SECURITY.md](SECURITY.md) how to report a security problem.
[Troubleshooting](docs/troubleshooting.md) covers the common errors.

## License

jsyn, the Java API, is licensed under Apache 2.0: see [LICENSE](LICENSE) and
[NOTICE](NOTICE). The engine in the `jsyn-natives-*` jars is licensed under the
[Synauson End User License Agreement](https://synauson.com/legal/eula/), which is also
`META-INF/LICENSE` in each natives jar. [How licensing connects](https://synauson.com/licensing/)
explains what a runtime sends to Synauson and why. The models the engine downloads and
the third-party code in the natives jars have their own licenses, listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
