# Installing jsyn's runtime on Linux and Windows

jsyn runs the Synauson engine inside your JVM. The jars come from Maven (see
[Install](../README.md#install) in the README); this page covers what the machine needs
around them: GStreamer, the Visual C++ runtime on Windows, the network and firewall,
and hardware for speech-to-text.

## Contents

- [What to install](#what-to-install)
- [Linux](#linux)
- [Windows](#windows)
- [Network and firewall](#network-and-firewall)
- [Sizing for STT](#sizing-for-stt)
- [NVIDIA GPUs](#nvidia-gpus)
- [Troubleshooting](#troubleshooting)

## What to install

| | Linux x86_64 | Windows x86_64 |
|---|---|---|
| Java | 11 or newer | 11 or newer |
| OS | glibc 2.34 or newer, with GStreamer 1.24 or 1.26 from the distribution ([which ones](#supported-distributions)) | Windows 10 or 11 |
| GStreamer | The distribution's packages ([below](#install-gstreamer)) | The 1.26.7 MSVC runtime installer ([below](#2-gstreamer)) |
| Visual C++ runtime | Not needed | The latest Microsoft Visual C++ v14 (2015 and later) Redistributable, x64 ([below](#1-visual-c-runtime)) |
| ONNX Runtime | Nothing to install: it ships inside `jsyn-natives-linux` | Nothing to install: it ships inside `jsyn-natives-windows` |
| NVIDIA software | Not needed ([why](#nvidia-gpus)) | Not needed |
| License key | Required, free tier included, from [synauson.com](https://synauson.com) | The same |

macOS and ARM are not supported. Don't set `ORT_DYLIB_PATH` for jsyn: the natives jar
loads its own ONNX Runtime.

## Linux

### Supported distributions

jsyn uses the distribution's GStreamer, so the GStreamer version the distribution ships
decides whether it works. GStreamer 1.28 changed the `webrtcbin` pad API, which breaks
WebRTC, so jsyn does not support it.

| Distribution | GStreamer | jsyn |
|---|---|---|
| Ubuntu 24.04 LTS | 1.24.2 | Supported |
| Debian 13 (trixie) | 1.26.2 | Supported |
| Ubuntu 26.04 LTS | 1.28.2 | Not supported (1.28) |
| Fedora 44 | 1.28.7 | Not supported (1.28) |
| Debian 12 (bookworm) | 1.22.0 | Too old |
| Ubuntu 22.04 LTS | 1.20.3 | Too old |
| RHEL 10 and derivatives | 1.26.7 | Not supported: no package provides `dtmfdetect` (GStreamer's spandsp plugin), which every SIP participant needs |

The versions are the candidates in each distribution's own repositories, checked with
`apt-cache policy libgstreamer1.0-0` or `dnf repoquery gstreamer1` in its official
container image (CentOS Stream 10 with EPEL for the RHEL 10 family). Fedora carries
`dtmfdetect` in `gstreamer1-plugins-bad-free-extras`, but its GStreamer is 1.28.

On another distribution, check its GStreamer version first, then install the
equivalent of the packages below. The ICE plugin (libnice's GStreamer plugin) often
ships as its own package.

### Install GStreamer

On Ubuntu 24.04 or Debian 13:

```bash
sudo apt-get update
sudo apt-get install -y libgstreamer1.0-0 gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-nice \
    gstreamer1.0-tools ca-certificates
```

- `gstreamer1.0-nice` is the ICE backend WebRTC needs, and is easy to miss.
- `gstreamer1.0-plugins-bad` provides `webrtcbin`, the SRTP elements, `dtmfdetect` and
  `errorignore`; `-good` the RTP and G.711 elements; `-base` Opus and the mixer.
- `gstreamer1.0-tools` provides `gst-inspect-1.0` for the check below. jsyn itself
  doesn't need it.
- `ca-certificates` is the trust store the engine uses for HTTPS to
  `license.synauson.com` and the model downloads from Cloudflare R2. Minimal images may lack it.

jsyn's CI also installs `gstreamer1.0-plugins-ugly` and `gstreamer1.0-libav`.

### Check the install

```bash
gst-inspect-1.0 --version
for e in errorignore webrtcbin nicesrc dtmfdetect srtpenc opusenc audiomixer; do
  gst-inspect-1.0 --exists "$e" || echo "missing: $e"
done; echo done
```

Nothing printed before `done` means every element is present. Then run the
[Quickstart](../README.md#quickstart) with `SYNAUSON_LICENSE_KEY` set.

## Windows

Run the PowerShell commands below in an elevated (Administrator) PowerShell. For a
complete Gradle project, see the
[Windows quickstart](https://github.com/synauson/examples/tree/main/java/jsyn-windows-quickstart).

### 1. Visual C++ runtime

`synauson_jni.dll` imports `VCRUNTIME140.dll`, and the bundled `onnxruntime.dll` also
imports `VCRUNTIME140_1.dll`, `MSVCP140.dll` and `MSVCP140_1.dll`. Many machines
already have them, but a clean Windows install doesn't, and GStreamer's `bin` folder
doesn't carry them. Install Microsoft's
[latest supported Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist)
for x64, from <https://aka.ms/vc14/vc_redist.x64.exe>:

```powershell
Start-BitsTransfer -Source 'https://aka.ms/vc14/vc_redist.x64.exe' -Destination "$env:TEMP\vc_redist.x64.exe"
Start-Process "$env:TEMP\vc_redist.x64.exe" -Wait -ArgumentList '/install', '/quiet', '/norestart'
Test-Path "$env:WINDIR\System32\VCRUNTIME140_1.dll"   # True
```

### 2. GStreamer

Install the GStreamer 1.26.7 MSVC x86_64 runtime installer,
[`gstreamer-1.0-msvc-x86_64-1.26.7.msi`](https://gstreamer.freedesktop.org/data/pkg/windows/1.26.7/msvc/gstreamer-1.0-msvc-x86_64-1.26.7.msi),
system-wide with every feature, into `C:\gstreamer`. 1.26.7 is the version jsyn's
Windows CI tests. You don't need the devel installer. In the graphical installer choose
**Complete**; silently:

```powershell
$gst = '1.26.7'
Start-BitsTransfer -Source "https://gstreamer.freedesktop.org/data/pkg/windows/$gst/msvc/gstreamer-1.0-msvc-x86_64-$gst.msi" -Destination "$env:TEMP\gst-runtime.msi"
Start-Process msiexec.exe -Wait -ArgumentList '/i', "$env:TEMP\gst-runtime.msi", '/quiet', '/norestart', 'ADDLOCAL=ALL', 'INSTALLDIR=C:\gstreamer\'
Test-Path 'C:\gstreamer\1.0\msvc_x86_64\bin\gstreamer-1.0-0.dll'   # True
```

A smaller install than Complete can leave out plugins the engine needs.

### 3. Environment variables

Windows loads `synauson_jni.dll`'s GStreamer libraries from `Path`. Set
`GSTREAMER_1_0_ROOT_MSVC_X86_64` and add GStreamer's `bin` folder to the machine `Path`:

```powershell
[Environment]::SetEnvironmentVariable('GSTREAMER_1_0_ROOT_MSVC_X86_64', 'C:\gstreamer\1.0\msvc_x86_64\', 'Machine')
$p = [Environment]::GetEnvironmentVariable('Path', 'Machine')
if ($p -notlike '*C:\gstreamer\1.0\msvc_x86_64\bin*') {
    [Environment]::SetEnvironmentVariable('Path', "$p;C:\gstreamer\1.0\msvc_x86_64\bin", 'Machine')
}
```

Sign out and back in, or restart the service that runs your application. A process
started before the change keeps the old `Path`.

### 4. Plugin registry

Once per Windows user account that runs the application, build GStreamer's plugin
registry:

```powershell
gst-inspect-1.0.exe coreelements
```

Without it, the first `new JSyn(...)` performs the scan, which took 7 to 44 seconds on
fresh CI machines. For a Windows service, run it as the service's account.

### 5. Check the install

From a new PowerShell window:

```powershell
where.exe gstreamer-1.0-0.dll        # C:\gstreamer\1.0\msvc_x86_64\bin\gstreamer-1.0-0.dll
gst-inspect-1.0.exe --version        # GStreamer 1.26.7
foreach ($e in 'errorignore','webrtcbin','nicesrc','dtmfdetect','srtpenc','opusenc','audiomixer') {
    gst-inspect-1.0.exe --exists $e; if ($LASTEXITCODE -ne 0) { "missing: $e" }
}
```

Nothing printed after the version means every element is present. Then run the
[Quickstart](../README.md#quickstart) with `SYNAUSON_LICENSE_KEY` set.

## Network and firewall

| Direction | Endpoint | Why |
|---|---|---|
| Outbound HTTPS | `license.synauson.com` | Exchanges the license key for a signed license at startup, and renews it about once a day |
| Outbound HTTPS | `*.r2.cloudflarestorage.com` (Cloudflare R2) | Downloads the models the license includes, once per model version: about 11 MB for `detect`; a `speech` license adds the STT model (about 660 MB) and the lettura-1 TTS model with its voices and pronunciation data (about 347 MB) |
| Outbound HTTPS | `maven.synauson.com` | The jsyn jars, at build time only |
| Outbound UDP 19302 | `stun.l.google.com` | The default STUN server for WebRTC; change it with `JSynConfig.Builder.webrtcStunServer` |
| Inbound and outbound UDP | The SIP RTP range | `rtpPortMin` to `rtpPortMax` (default 10000 to 20000) |
| Inbound and outbound UDP, and TCP | The WebRTC ICE range | `webrtcIcePortRange(min, max)`; unset, ICE uses any free port |

The JVM sends and receives media itself, so open these ranges to the Java process. Set a
WebRTC range on any machine behind a firewall and keep it clear of the RTP range. On a
host with no internet access, see [Licensing and models](../README.md#licensing-and-models).

On Linux with ufw, for example, with a WebRTC range of 40000 to 40999:

```bash
sudo ufw allow 10000:20000/udp
sudo ufw allow 40000:40999/udp
sudo ufw allow 40000:40999/tcp
```

On Windows, Defender Firewall may ask whether to allow `java.exe` the first time it
listens. On a server nobody answers, so inbound media stays blocked. Add rules for the
Java executable that runs your application, with your ranges:

```powershell
$java = (Get-Command java.exe).Source   # or the full path your service uses
New-NetFirewallRule -DisplayName 'jsyn SIP RTP' -Direction Inbound -Action Allow `
    -Program $java -Protocol UDP -LocalPort 10000-20000
New-NetFirewallRule -DisplayName 'jsyn WebRTC ICE (UDP)' -Direction Inbound -Action Allow `
    -Program $java -Protocol UDP -LocalPort 40000-40999
New-NetFirewallRule -DisplayName 'jsyn WebRTC ICE (TCP)' -Direction Inbound -Action Allow `
    -Program $java -Protocol TCP -LocalPort 40000-40999
```

## Sizing for STT

Streaming speech-to-text is by far the heaviest thing the engine runs. The figures
below are Synauson's own measurements with the spartito-1 int8 model; treat them as a
guide, not a guarantee, and check the numbers your own machine reports.

- On current Intel server CPUs, STT ran about one real-time stream per vCPU. Older
  Intel server CPUs and AMD Genoa ran about half that.
- An 8-core AVX2 desktop CPU (Ryzen 7 3700X) carried about 2 streams.
- STT workers share one copy of the model's weights, about 600 MB of memory paid once,
  mapped from a file the engine writes into the model store on its first STT start
  (about 1.2 GB on disk). With a read-only model store each worker loads a private copy
  instead, about 800 MB each. `capabilities().stt.sharedModelBytes` and `modelBytes`
  report the split.
- VAD and turn detection cost about 0.01 CPU core per call.
- The [turn flush](../README.md#configuration-and-logging) (`sttTurnFlush`), when on,
  costs about 26% more CPU and lowers the stream cap by about a quarter.

The engine measures the machine when the STT pool starts (a timed decode, and the
memory one worker's model copy takes) and sets the pool and its stream cap from that.
`jsyn.capabilities().stt` reports the result: `streams.limit` is the cap,
`realTimeFactor` one stream's decode time over audio time, and `limitedBy` whether CPU
or memory set it. The measurement is of one stream alone, so on a shared or busy host
it can be optimistic. `JSynConfig.Builder.sttCapacity(workers, threads, maxStreams)`
overrides it. Each STT participant also takes one of the license's concurrent AI
sessions. The pool sizes
itself from the runtime's CPU budget, a container's or service's CPU quota and cpuset
rather than the host's CPU count (`capabilities().resources`); `cpuBudget` and
`memoryBudget` set it by hand.

The engine also times one VAD chunk and one turn detection decision in the background at
startup. It keeps every timing in `calibration.json` in the state directory
(`JSynConfig.Builder.stateDir`), and a later start reuses a timing instead of measuring
again when the model, the CPU and its features, the CPU budget, the thread count and the
ONNX Runtime version all match. Anything else is measured again and replaces the entry.
A timing taken while the CPU was busy (Linux, from the kernel's pressure figures) is used
for that run but not cached. `capabilities().stt.source` says whether STT's numbers were
measured on this start (`auto`), read from the cache (`cached`) or set with
`sttCapacity` (`override`), and `capabilities().calibration` gives the cache file and
the detector timings. After changing hardware in place, or to measure again on an idle
host, start once with `recalibrate(true)`. Runtimes that share a state directory share
the file, and the last one to write it wins.

## NVIDIA GPUs

The engine runs all inference on the CPU, through ONNX Runtime's CPU execution provider.
An NVIDIA GPU is not used, and no NVIDIA driver, CUDA or cuDNN is needed. GPU support,
through ONNX Runtime's CUDA execution provider with the CUDA and cuDNN libraries you
install yourself, is planned.

## Troubleshooting

The README's [troubleshooting table](../README.md#troubleshooting) covers jsyn's own
errors. For the installation:

| Symptom | Cause and fix |
|---|---|
| `UnsatisfiedLinkError: ...onnxruntime.dll: Can't find dependent libraries` | The Visual C++ runtime is missing or too old. Install it ([Windows step 1](#1-visual-c-runtime)). |
| `UnsatisfiedLinkError: ...synauson_jni.dll: Can't find dependent libraries` | GStreamer's `bin` folder isn't on the process's `Path` ([step 3](#3-environment-variables)), or the Visual C++ runtime is missing ([step 1](#1-visual-c-runtime)). Run the [check](#5-check-the-install) as the account that runs the application. |
| Works in a terminal, fails as a service or in the IDE | That process started before `Path` changed, or runs as another user. Restart it. |
| `UnsatisfiedLinkError` naming a `libgst…` library on Linux | GStreamer is missing. Install the [packages](#install-gstreamer). |
| `gst-inspect-1.0: command not found` | Install `gstreamer1.0-tools`. |
| `GStreamer sanity check failed: required GStreamer element '…' not found` | A plugin package is missing (Linux), or GStreamer was installed without the Complete profile (Windows). |
| Adding a SIP participant throws `InternalException` naming `dtmfdetect` | The distribution's GStreamer lacks the spandsp plugin, as on RHEL. Use a [supported distribution](#supported-distributions). |
| WebRTC participants never connect while SIP works | The ICE plugin (`gstreamer1.0-nice`) is missing, GStreamer is 1.28, or the ICE port range is blocked. |
| SIP calls connect but carry no audio | Inbound UDP to the RTP range is blocked for the Java process. |
| First `new JSyn(...)` on Windows takes tens of seconds | The plugin registry isn't built for this user ([step 4](#4-plugin-registry)). |
