# Installing the runtime

jsyn runs the Synauson engine inside your JVM. The jars come from Maven (see
[Install](../README.md#install)). This page covers what the machine needs around them:
GStreamer, the Visual C++ runtime on Windows, network access and firewall rules.

- [What to install](#what-to-install)
- [Linux](#linux)
- [Windows](#windows)
- [Network and firewall](#network-and-firewall)
- [NVIDIA GPUs](#nvidia-gpus)
- [Troubleshooting the install](#troubleshooting-the-install)

## What to install

| | Linux x86_64 | Windows x86_64 |
|---|---|---|
| Java | 11 or newer | 11 or newer |
| Operating system | glibc 2.34 or newer, and a [distribution that ships GStreamer 1.24 or 1.26](#supported-distributions) | Windows 10 or 11 |
| GStreamer | The distribution's packages ([steps](#install-gstreamer)) | The 1.26.7 MSVC runtime installer ([steps](#2-install-gstreamer)) |
| Visual C++ runtime | Not needed | The latest Microsoft Visual C++ Redistributable, x64 ([steps](#1-install-the-visual-c-runtime)) |
| ONNX Runtime | Nothing to install: it is inside `jsyn-natives-linux` | Nothing to install: it is inside `jsyn-natives-windows` |
| NVIDIA software | Not needed ([why](#nvidia-gpus)) | Not needed |

You also need a license key from Synauson; see
[Licensing and models](licensing-and-models.md). macOS and ARM are not supported. Don't
set `ORT_DYLIB_PATH` for jsyn: the natives jar loads its own ONNX Runtime.

## Linux

### Supported distributions

jsyn uses the distribution's GStreamer, so the distribution's GStreamer version decides
whether jsyn works there. GStreamer 1.28 changed the `webrtcbin` API that WebRTC
participants use, so jsyn does not support 1.28.

| Distribution | GStreamer | jsyn |
|---|---|---|
| Ubuntu 24.04 LTS | 1.24.2 | Supported |
| Debian 13 (trixie) | 1.26.2 | Supported |
| Ubuntu 26.04 LTS | 1.28.2 | Not supported (1.28) |
| Fedora 44 | 1.28.7 | Not supported (1.28) |
| Debian 12 (bookworm) | 1.22.0 | Not supported (too old) |
| Ubuntu 22.04 LTS | 1.20.3 | Not supported (too old) |
| RHEL 10 and its rebuilds | 1.26.7 | Not supported: no package provides `dtmfdetect` (GStreamer's spandsp plugin), which every SIP participant needs |

The versions are what each distribution's own repositories offer, checked in its
official container image (CentOS Stream 10 with EPEL for the RHEL 10 family). On a
distribution not listed here, check its GStreamer version first, then install the
equivalent of the packages below. The ICE plugin often comes in a package of its own.

### Install GStreamer

On Ubuntu 24.04 or Debian 13:

```bash
sudo apt-get update
sudo apt-get install -y libgstreamer1.0-0 gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-nice \
    gstreamer1.0-tools ca-certificates
```

| Package | What jsyn uses from it |
|---|---|
| `gstreamer1.0-plugins-base` | Opus and the audio mixer |
| `gstreamer1.0-plugins-good` | RTP and G.711 |
| `gstreamer1.0-plugins-bad` | `webrtcbin`, SRTP, `dtmfdetect` and `errorignore` |
| `gstreamer1.0-nice` | ICE for WebRTC. It is easy to miss, and WebRTC fails without it. |
| `gstreamer1.0-tools` | `gst-inspect-1.0`, for the check below. jsyn doesn't need it. |
| `ca-certificates` | The trust store for HTTPS to the licensing server and the model downloads. Minimal container images may lack it. |

### Check the install

```bash
gst-inspect-1.0 --version
for e in errorignore webrtcbin nicesrc dtmfdetect srtpenc opusenc audiomixer; do
  gst-inspect-1.0 --exists "$e" || echo "missing: $e"
done; echo done
```

The first line prints the GStreamer version. If nothing else appears before `done`,
every element jsyn needs is there. Then run the [Quickstart](../README.md#quickstart)
with `SYNAUSON_LICENSE_KEY` set.

## Windows

Run the PowerShell commands below in a PowerShell window opened as Administrator. For a
complete Gradle project that uses these steps, see the
[Windows quickstart](https://github.com/synauson/examples/tree/main/java/jsyn-windows-quickstart).

### 1. Install the Visual C++ runtime

The engine's DLL and the ONNX Runtime DLL inside the natives jar link against the
Microsoft Visual C++ runtime (`VCRUNTIME140.dll`, `VCRUNTIME140_1.dll`, `MSVCP140.dll`
and `MSVCP140_1.dll`). Many machines already have it, but a clean Windows install
doesn't. Install Microsoft's
[latest supported Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist)
for x64:

```powershell
Start-BitsTransfer -Source 'https://aka.ms/vc14/vc_redist.x64.exe' -Destination "$env:TEMP\vc_redist.x64.exe"
Start-Process "$env:TEMP\vc_redist.x64.exe" -Wait -ArgumentList '/install', '/quiet', '/norestart'
Test-Path "$env:WINDIR\System32\VCRUNTIME140_1.dll"   # True
```

### 2. Install GStreamer

Install the GStreamer 1.26.7 MSVC x86_64 runtime installer,
[`gstreamer-1.0-msvc-x86_64-1.26.7.msi`](https://gstreamer.freedesktop.org/data/pkg/windows/1.26.7/msvc/gstreamer-1.0-msvc-x86_64-1.26.7.msi),
for all users, with every feature, into `C:\gstreamer`. This is the version jsyn is
tested with on Windows. You don't need the devel installer. In the installer's window,
choose **Complete**. To install it without the window:

```powershell
$gst = '1.26.7'
Start-BitsTransfer -Source "https://gstreamer.freedesktop.org/data/pkg/windows/$gst/msvc/gstreamer-1.0-msvc-x86_64-$gst.msi" -Destination "$env:TEMP\gst-runtime.msi"
Start-Process msiexec.exe -Wait -ArgumentList '/i', "$env:TEMP\gst-runtime.msi", '/quiet', '/norestart', 'ADDLOCAL=ALL', 'INSTALLDIR=C:\gstreamer\'
Test-Path 'C:\gstreamer\1.0\msvc_x86_64\bin\gstreamer-1.0-0.dll'   # True
```

A smaller install than Complete can leave out plugins the engine needs.

### 3. Set the environment variables

Windows finds GStreamer's DLLs through `Path`. Set `GSTREAMER_1_0_ROOT_MSVC_X86_64` and
add GStreamer's `bin` folder to the machine `Path`:

```powershell
[Environment]::SetEnvironmentVariable('GSTREAMER_1_0_ROOT_MSVC_X86_64', 'C:\gstreamer\1.0\msvc_x86_64\', 'Machine')
$p = [Environment]::GetEnvironmentVariable('Path', 'Machine')
if ($p -notlike '*C:\gstreamer\1.0\msvc_x86_64\bin*') {
    [Environment]::SetEnvironmentVariable('Path', "$p;C:\gstreamer\1.0\msvc_x86_64\bin", 'Machine')
}
```

A process started before this change keeps the old `Path`. Sign out and back in, and
restart any service or IDE that will run your application.

### 4. Build the plugin registry

Once for each Windows account that runs your application, build GStreamer's plugin
registry from a new PowerShell window:

```powershell
gst-inspect-1.0.exe coreelements
```

If you skip this, the first `new JSyn(...)` for that account scans the plugins itself,
which can take tens of seconds. For a Windows service, run it as the service's account.

### 5. Check the install

From a new PowerShell window:

```powershell
where.exe gstreamer-1.0-0.dll        # C:\gstreamer\1.0\msvc_x86_64\bin\gstreamer-1.0-0.dll
gst-inspect-1.0.exe --version        # GStreamer 1.26.7
foreach ($e in 'errorignore','webrtcbin','nicesrc','dtmfdetect','srtpenc','opusenc','audiomixer') {
    gst-inspect-1.0.exe --exists $e; if ($LASTEXITCODE -ne 0) { "missing: $e" }
}
```

If nothing prints after the version, every element jsyn needs is there. Then run the
[Quickstart](../README.md#quickstart) with `SYNAUSON_LICENSE_KEY` set.

## Network and firewall

| Direction | Endpoint | Why |
|---|---|---|
| Outbound HTTPS | `usage.synauson.com` | Required: session-count reports every 30 seconds (the license's pool is shared by all its runtimes) and license renewal at startup and about once a day. Up to 7 days without it are fine; an air-gapped license needs none |
| Outbound HTTPS | `license.synauson.com` | Model downloads, and any start without a cached license file |
| Outbound HTTPS | `*.r2.cloudflarestorage.com` (Cloudflare R2) | Downloads the models, once per model release: about 11 MB for detection, 660 MB for speech to text and 372 MB for text to speech. `license.synauson.com` redirects each file here |
| Outbound HTTPS | `status.synauson.com` | Synauson's outage notices. While one is up, a runtime whose reports fail keeps its 7 days paused; a runtime that can't read it gets no pause |
| Outbound UDP 123 (NTP) | your time source | Keep the clock synchronized: licensing answers count only within 5 minutes of it, and outage notices within 10 |
| Outbound HTTPS | `maven.synauson.com` | The jsyn jars, at build time only |
| Outbound UDP (the STUN server's port) | the STUN server you set | Only with `JSynConfig.Builder.webrtcStunServer`; there is none by default. |
| Inbound and outbound UDP | The SIP media range | `rtpPortMin` to `rtpPortMax`, by default 10000 to 20000 |
| Inbound and outbound UDP and TCP | The WebRTC media range | `webrtcIcePortRange(min, max)`. Unset, ICE uses any free port. |

The JVM sends and receives the media itself, so open these ranges to the Java process. On
a machine behind a firewall, set a WebRTC range, and keep it clear of the SIP range. On a
host with no internet access, see
[Run without internet access](licensing-and-models.md#run-without-internet-access).

With ufw on Linux, for example, and a WebRTC range of 40000 to 40999:

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

## NVIDIA GPUs

The engine runs all inference on the CPU. It doesn't use an NVIDIA GPU, so you need no
NVIDIA driver, CUDA or cuDNN for jsyn. A GPU in the machine does no harm.
[Capacity and sizing](sizing.md) says how many calls a CPU carries.

## Troubleshooting the install

| Symptom | Cause and fix |
|---|---|
| `UnsatisfiedLinkError: ...onnxruntime.dll: Can't find dependent libraries` | The Visual C++ runtime is missing or too old. Install it ([step 1](#1-install-the-visual-c-runtime)). |
| `UnsatisfiedLinkError: ...synauson_jni.dll: Can't find dependent libraries` | GStreamer's `bin` folder isn't on this process's `Path` ([step 3](#3-set-the-environment-variables)), or the Visual C++ runtime is missing ([step 1](#1-install-the-visual-c-runtime)). Run the [check](#5-check-the-install) as the account that runs your application. |
| It works in a terminal but not as a service or in the IDE | That process started before `Path` changed, or runs as another user. Restart it. |
| `UnsatisfiedLinkError` naming a `libgst…` library on Linux | GStreamer is missing. Install the [packages](#install-gstreamer). |
| `gst-inspect-1.0: command not found` | Install `gstreamer1.0-tools`. |
| `InternalException: INTERNAL: GStreamer sanity check failed: required GStreamer element '…' not found` | A plugin package is missing on Linux, or GStreamer was installed without the Complete feature set on Windows. |
| Adding a SIP participant throws an `InternalException` naming `dtmfdetect` | The distribution's GStreamer has no spandsp plugin, as on RHEL. Use a [supported distribution](#supported-distributions). |
| WebRTC participants never connect, while SIP works | The ICE plugin (`gstreamer1.0-nice`) is missing, GStreamer is 1.28, or a firewall blocks the WebRTC port range. |
| SIP calls connect but carry no audio | Inbound UDP to the SIP media range is blocked for the Java process. |
| The first `new JSyn(...)` on Windows takes tens of seconds | The plugin registry isn't built for this account ([step 4](#4-build-the-plugin-registry)). |

[Troubleshooting](troubleshooting.md) covers the errors you may see once the runtime
starts.
