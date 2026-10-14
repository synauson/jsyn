# Troubleshooting

Find the message or symptom you see, then its cause and fix. For anything not here, run
again with `SYNAUSON_LOG_LEVEL=debug` set before the JVM starts, and read the engine's
log on stderr. If you still need help, contact Synauson with the details listed in
[SUPPORT.md](../SUPPORT.md).

- [Loading the natives](#loading-the-natives)
- [GStreamer](#gstreamer)
- [License](#license)
- [Models, speech to text and text to speech](#models-speech-to-text-and-text-to-speech)
- [Calls and media](#calls-and-media)

## Loading the natives

| Message or symptom | Cause and fix |
|---|---|
| `UnsatisfiedLinkError: missing native: com/synauson/jsyn/natives/<platform>/… — add jsyn-natives-<platform> to your classpath` | The natives jar for this operating system isn't on the runtime classpath. Add `jsyn-natives-linux` or `jsyn-natives-windows` ([Install](../README.md#install)). |
| `UnsatisfiedLinkError: jsyn does not yet support OS '…'` (or `arch`) | jsyn runs on Linux and Windows on x86_64 only. |
| `UnsatisfiedLinkError: …onnxruntime.dll: Can't find dependent libraries` on Windows | The Visual C++ runtime is missing. [Install it](install.md#1-install-the-visual-c-runtime). |
| `UnsatisfiedLinkError: …synauson_jni.dll: Can't find dependent libraries` on Windows | GStreamer's `bin` folder isn't on this process's `Path`, or the Visual C++ runtime is missing. See [Windows](install.md#windows). |
| `UnsatisfiedLinkError` naming a `libgst…` library, or `gstreamer-1.0-0.dll` | GStreamer isn't installed, or isn't on the library path. See [Installing the runtime](install.md). On Windows, sign out and back in after changing `Path`. |
| It works in a terminal but not as a service or in the IDE | That process started before `Path` changed, or runs as another user. Restart it. |

## GStreamer

| Message or symptom | Cause and fix |
|---|---|
| `InternalException: INTERNAL: GStreamer sanity check failed: required GStreamer element '…' not found` | A plugin package is missing. `errorignore` comes from `gstreamer1.0-plugins-bad`, the mixer and codecs from `-base` and `-good`. On Windows, reinstall GStreamer with the Complete feature set. |
| Adding a SIP participant throws an `InternalException` naming `dtmfdetect` | GStreamer has no spandsp plugin, as on RHEL. Use a [supported distribution](install.md#supported-distributions). |
| WebRTC participants fail or never connect, while SIP and file participants work | The ICE plugin (`gstreamer1.0-nice`) is missing, GStreamer is 1.28, or a firewall blocks the WebRTC ports. |
| The first `new JSyn(...)` on Windows takes tens of seconds | GStreamer is building its plugin registry. Run `gst-inspect-1.0.exe coreelements` once for each Windows account ([step 4](install.md#4-build-the-plugin-registry)). |

## License

| Message or symptom | Cause and fix |
|---|---|
| `InvalidArgumentException: INVALID_ARGUMENT: no license key configured: set SYNAUSON_LICENSE_KEY …` | Set the variable, or pass `licenseKey(...)`. |
| `PermissionDeniedException: NO_LICENSE: synauson needs a valid license to start: …` from `new JSyn(...)` | There is no license file that still works: the runtime is offline (or can't reach `usage.synauson.com`) and its cached or configured file is missing, or more than 7 days past the last usage report that got through. Check access to `usage.synauson.com` and `license.synauson.com`. If the message says offline mode needs the usage receipt, the runtime has `offline(true)` and its state directory has no `usage/receipt.json` from an online run: run it once without `offline(true)` on that state directory, or use an air-gapped license ([Run without internet access](licensing-and-models.md#run-without-internet-access)). |
| `PermissionDeniedException: LICENSE_REJECTED: license.synauson.com refused this license key: …` | The key is unknown, suspended or banned. An expired license is not refused (it runs on the Free plan); see [License states](licensing-and-models.md#license-states). A key issued for jsyn 1.5 or earlier doesn't work with 1.6 or newer ([Upgrading](upgrading.md#from-15-to-16)). |
| `FailedPreconditionException: ILLEGAL_LICENSE: … is not a valid synauson license: …` from `new JSyn(...)` | The license itself doesn't fit this engine. The message lists every problem. No setting fixes it: send the message to Synauson for a corrected license. |
| `PermissionDeniedException: LICENSE_REJECTED: cannot add a participant to conference '…': …` or `… cannot start conference '…': …` (`LICENSE_DISCONNECTED` after 7 days without a usage report, `LICENSE_EXPIRED` for an expired developer key) | The license starts nothing new: `capabilities().license.state` is `rejected`, `disconnected` (below) or `expired` (a developer key: ask Synauson to renew it). Every license otherwise includes every capability. |
| `LimitExceededException: SESSION_LIMIT_REACHED: concurrent AI session limit reached: …` | Every AI session the license allows, and its burst, is in use. Remove a participant with AI, add the new one without, or ask Synauson for more sessions. `capabilities().sessions` shows the limit, use and peak. |
| `LimitExceededException: CONFERENCE_POOL_LIMIT_REACHED: …` from `startConference` | Every conference the license allows, and its burst, is in use (every live conference counts, with AI or without; the Free base is 10). End a conference or ask Synauson for more conferences. `capabilities().conferences` shows the limit, use and peak. `CONFERENCE_LIMIT_REACHED` is your own `maxConferences` instead. |
| `FailedPreconditionException: LICENSE_SHARE_PENDING: no new conference can start: …` (or `no new AI session can start`) | The runtime holds no share of the license's pool yet, usually because no usage report has got through since it started. The pool isn't full. Retry shortly, and check access to `usage.synauson.com` if it lasts. |
| `capabilities().license.state` is `offline` or `disconnected` | The runtime's usage reports are not reaching `usage.synauson.com`. Its file works for 7 days after the last one that did (`offline`; `capabilities().usageService.graceEnds` says when); after that no new conference or participant starts (`disconnected`) until a report gets through. Check access to `usage.synauson.com`. |
| `LimitExceededException` with `capabilities().license.state` `expired` | The license expired, so the runtime is on the Free plan, 4 AI sessions and 10 conferences. Renew it; the full plan comes back without a restart. |

## Models, speech to text and text to speech

| Message or symptom | Cause and fix |
|---|---|
| `FailedPreconditionException: MODEL_LOADING: …` | A model is still downloading, or the STT or TTS pool is still loading, usually right after a start. Retry in a few seconds; `capabilities().models`, `stt` and `tts` show the state. |
| `FailedPreconditionException: MODEL_UNAVAILABLE: model 'sentito-1' is not installed: …` (or another model) | The model isn't in the store and isn't downloading: often the host can't reach Cloudflare R2. Wait until `capabilities().models` reports it `ready`; its `detail` says why it isn't. On an offline host, run [`JSyn.importModels`](licensing-and-models.md#run-without-internet-access). |
| `FailedPreconditionException` adding a participant with STT or a speaker, with the models ready | The STT pool or the TTS engine is still loading. Wait for `capabilities().stt.state` or `tts.state` to be `ready`. |
| `InvalidArgumentException: INVALID_ARGUMENT: turn_detection needs vad on the same participant: …` (or `stt needs turn_detection …`) | Each detector needs the one before it on the same participant. Add the `VadConfig` (or `TurnDetectionConfig`) it names. |
| `LimitExceededException` adding a participant with STT | Every STT stream this machine admits is in use. See [Capacity and sizing](sizing.md). |
| `STT_LAGGING` on the agent stream, or turns ending incomplete (`complete` false) with their last words in the next turn or a late turn | The machine has too little CPU for its STT streams. Lower `sttCapacity`'s `maxStreams`, or give the runtime more CPU. |
| `UtteranceDone.underrunMs` above 0 | Synthesis fell behind playback. Raise `ttsCapacity` or give the runtime more CPU. |
| `InvalidArgumentException` with eager on and STT | Eager end of turn with STT needs `sttEager(true)` on the runtime. |

## Calls and media

| Message or symptom | Cause and fix |
|---|---|
| A participant hears nothing | Nothing is routed to it. Adding participants connects nothing: set the routes with `updatePartyAudioConnections` ([Routing audio](conferences.md#routing-audio)). |
| SIP calls connect but carry no audio | Inbound UDP to the SIP port range is blocked for the Java process ([Network and firewall](install.md#network-and-firewall)), or the peer's address in your SDP is wrong. |
| WebRTC connects on a LAN but not across the internet | The peers can't reach each other directly. Add a TURN server ([WebRTC calls](webrtc.md#turn)). |
| `NativeParticipant.write` returns less than you gave it | The ingress ring is full: you are writing faster than real time ([Native participants](native-participants.md#threads-and-pacing)). |
| An observer stops getting agent events, with `AGENT_SUBSCRIBER_LAGGED` | The observer did work on the engine thread and fell behind. Queue events and resume ([Voice agents](voice-agents.md#keeping-up-with-the-stream)). |
| `NotFoundException` from a `Conference` you didn't close | The conference was ended, for example by `shutdownServer`. |
