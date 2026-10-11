# Upgrading from jsyn 1.5 to 1.6

jsyn 1.6.0 runs on jsyn-natives 1.7.1 or newer. The upgrade changes the license key,
renames the turn-detection API, and downloads the models again. Plan it as one deploy:
the new key works only with the new natives, and the old natives don't accept it.

- [Before you start](#before-you-start)
- [1. Update the dependencies](#1-update-the-dependencies)
- [2. Replace the license key](#2-replace-the-license-key)
- [3. Rename the turn-detection API](#3-rename-the-turn-detection-api)
- [4. Put VAD next to turn detection](#4-put-vad-next-to-turn-detection)
- [5. Check what you read from capabilities()](#5-check-what-you-read-from-capabilities)
- [6. Let the models download again](#6-let-the-models-download-again)
- [7. Set a STUN server if peers are behind NAT](#7-set-a-stun-server-if-peers-are-behind-nat)
- [Check that it worked](#check-that-it-worked)
- [If something goes wrong](#if-something-goes-wrong)
- [Other behaviour changes](#other-behaviour-changes)
- [What's new](#whats-new)

## Before you start

- Get your new license key from Synauson. Keys issued for jsyn 1.5 and earlier don't
  work with natives 1.7 and newer, and older natives can't check the new keys.
- Java 11, the supported operating systems and GStreamer 1.24 or 1.26 are unchanged
  ([Installing the runtime](install.md)).
- Each host needs HTTPS to `usage.synauson.com` (usage reports and license renewal;
  the license's session pool is shared by all its runtimes), `license.synauson.com`,
  `status.synauson.com` (Synauson's outage notices) and Cloudflare R2
  (`*.r2.cloudflarestorage.com`), where the models now download from, and a clock kept
  in sync with NTP ([Network and firewall](install.md#network-and-firewall)).
- Leave about 2.5 GB free for the model store
  ([6. Let the models download again](#6-let-the-models-download-again)).
- If you run hosts offline, with `offline(true)` and a `licenseFile`, ask Synauson for
  an air-gapped license file and the model files before you upgrade.

## 1. Update the dependencies

Use jsyn 1.6.0 with jsyn-natives 1.7.1. The coordinates and the repository URL are
unchanged, but `https://maven.synauson.com/releases` now serves only jsyn 1.6.0 and
natives 1.7.1 and newer: a build pinned to an older version no longer resolves.

```kotlin
val jsynVersion = "1.6.0"
val jsynNativesVersion = "1.7.1"

dependencies {
    implementation("com.synauson:jsyn:$jsynVersion")
    runtimeOnly("com.synauson:jsyn-natives-linux:$jsynNativesVersion")
    runtimeOnly("com.synauson:jsyn-natives-windows:$jsynNativesVersion")
}
```

[Install](../README.md#install) has the full Gradle and Maven setup. Upgrade jsyn and
the natives together: jsyn 1.5.0 can't use turn detection with natives 1.7, and the
natives now refuse settings they don't recognise.

## 2. Replace the license key

jsyn reads the key as before: from `JSynConfig.Builder.licenseKey(...)`, or from
`SYNAUSON_LICENSE_KEY` when that is null. Replace the old value wherever you set it.

You don't need to clear the state directory. The engine sees that the license cached
there belongs to the old key and caches the new license over it.

## 3. Rename the turn-detection API

Turn detection is now named after its role throughout the API. In 1.5 these names
carried the name of the turn-detection model the engine used then; the table writes
that part of each old name as `Xx`. The compiler finds every use in Java code. The JSON
rows matter only if you build participant JSON yourself.

| 1.5 | 1.6 |
|---|---|
| `spec.XxTurnConfig` | `spec.TurnDetectionConfig`, threshold 0.5 as before; the default `bufferedSamples` is 4000 (250 ms), the least the engine applies, where 1.5's 160 was never applied |
| `event.XxTurnEvent` and `XxTurnEvent.TurnResult` | `event.TurnDetectionEvent` and `TurnDetectionEvent.TurnResult`, with the same fields |
| `Conference.streamXxTurnEvents(id, observer)` | `Conference.streamTurnDetectionEvents(id, observer)` |
| `.xxTurn(config)` on the `FileParticipantSpec`, `NativeParticipantSpec`, `SipParticipantSpec`, `SipConnectionSpec` and `WebRtcParticipantSpec` builders, and the `xxTurn` field | `.turnDetection(config)` and the `turnDetection` field |
| JSON key `xx_turn` (file, native and WebRTC specs) | `turn_detection` |
| JSON key `xxTurn` (SIP specs) | `turnDetection` |

`VadConfig`, `VadEvent`, `streamVadEvents` and the rest of the 1.5 API keep their names
and behaviour.

## 4. Put VAD next to turn detection

A participant with `TurnDetectionConfig` now needs a `VadConfig` on the same spec, since
turn detection decides on the pauses VAD finds. In 1.5 such a participant was accepted
but never produced a turn event. Now adding it throws:

```text
InvalidArgumentException: turn_detection needs vad on the same participant: ...
```

## 5. Check what you read from capabilities()

Skip this step if your code doesn't call `jsyn.capabilities()`.

A license now has a plan and one pool of AI sessions, where 1.5 licenses counted
conferences and streams. Each participant with AI takes one session
([Plans and AI sessions](licensing-and-models.md#plans-and-ai-sessions)). The report
changed with it:

| 1.5 | 1.6 |
|---|---|
| `caps.conferences`, `caps.aiConferences` | Deprecated and now null. Read `caps.sessions` (`limit`, `inUse`, `peak`); a null `limit` is unlimited. |
| `CapabilityInfo.streams` | Now null. Read `caps.sessions`. |
| `license.state` `"licensed"` or `"free-tier-floor"` | `valid`, `expiring`, `offline`, `expired`, `disconnected`, `invalid-kept-last-valid` or `rejected` ([License states](licensing-and-models.md#license-states)). Without a license file that still works (7 days after the last usage report that got through) `new JSyn` throws `PermissionDeniedException` |
| | New: `license.plan`, `license.daysRemaining`, `license.problem` |
| `ModelInfo.id`, the old model ids | `sentito-1` (VAD), `fermata-1` (turn detection), `spartito-1` (speech to text), `lettura-2` (text to speech) |
| `ModelInfo.version`, a version directory | The model variant, such as `cpu` or `int8` |
| `ModelInfo.state` | Adds `downloading` |

Code that reads `caps.conferences.limit` throws `NullPointerException` after the
upgrade. Code that matches a model id or a `license.state` value needs the new values.
`JSyn.importModels` also returns the new ids.

## 6. Let the models download again

The model store has a new layout, `<model id>/<variant>/<file>`, so the first start
downloads the models again, in the background. The store's location doesn't change.

| What | Size |
|---|---|
| VAD and turn-detection models | about 11 MB |
| Speech to text model | about 660 MB |
| Text to speech model, voices and pronunciation data | about 372 MB |
| Speech to text weights prepared for the CPU, written on the first start that uses speech to text | about 1.2 GB |

Until a model has downloaded, adding a participant that needs it throws
`FailedPreconditionException` naming the model, as in 1.5. Once the new release runs,
delete every directory in the store other than `sentito-1`, `fermata-1`, `spartito-1`
and `lettura-2`.

## 7. Set a STUN server if peers are behind NAT

`JSynConfig.Builder.webrtcStunServer` now defaults to none (`""`). jsyn 1.5 passed
Google's public STUN server, `stun://stun.l.google.com:19302`. If WebRTC peers reach
the runtime through NAT, set your STUN server (or that one) explicitly; see
[WebRTC calls](webrtc.md).

## Check that it worked

Start once with `SYNAUSON_LOG_LEVEL=info`, set before the JVM starts. The engine logs a
`license established` line with `standing=valid` and your plan, then `downloading model`
and `model downloaded` for each model. From code, `capabilities()` shows the same
([Check what the runtime may use](licensing-and-models.md#check-what-the-runtime-may-use)).

## If something goes wrong

| What you see | Cause and fix |
|---|---|
| `InvalidArgumentException: json parse failed: unknown field ...` | jsyn and the natives don't match. Use jsyn 1.6.0 with natives 1.7.1 or newer, and check that no older `jsyn-natives-*` jar is still on the classpath. |
| `UnsatisfiedLinkError` or `NoClassDefFoundError` naming a turn-detection class or method | The same: mixed versions of jsyn and the natives. |
| `PermissionDeniedException: license.synauson.com refused this license key: ...` | The process still uses the old key. Check `SYNAUSON_LICENSE_KEY` and `licenseKey(...)` in the environment the service really runs in. |
| `InvalidArgumentException: turn_detection needs vad on the same participant` | See [step 4](#4-put-vad-next-to-turn-detection). |
| `FailedPreconditionException: model 'fermata-1' is not installed` | The model is still downloading, or the host can't reach Cloudflare R2. Check `capabilities().models`. |
| `NullPointerException` reading `capabilities()` | See [step 5](#5-check-what-you-read-from-capabilities). |

## Other behaviour changes

These need no change unless your code relies on the old behaviour:

- Mistakes in a request throw their own exception, not `InternalException`: an id
  already in use `AlreadyExistsException`; a WebRTC offer that isn't SDP with one audio
  stream, or a file that can't be played, `InvalidArgumentException`; a missing file or
  an unknown participant in a route, DTMF stream or detector stream `NotFoundException`.
  Priority audio on a participant that isn't a file participant throws
  `FailedPreconditionException`. [Errors](errors.md) lists them all.
- `VadEvent.SpeechEnd.durationMs` is the length of the speech that ended, where it used
  to be the silence that confirmed the end.
- `TurnDetectionConfig`'s `bufferedSamples` takes effect: values from 0 to 4000 mean
  4000, and a negative value or more than 128000 throws `InvalidArgumentException`.
- `muteParticipant` mutes native participants, and routes added while a participant is
  muted start muted.
- A file that can't be played sends `FileEvent.FileError`. When that is found at the
  add, the add throws too.
- File participants play only WAV (PCM, A-law or mu-law), Ogg Opus, Ogg Vorbis, FLAC
  and MP3. Any other format, such as AAC or M4A, throws `InvalidArgumentException`.
- RFC 4733 events on an Opus SIP call run on the codec's 48 kHz clock: negotiate
  `telephone-event/48000` with Opus (and `/8000` with PCMU or PCMA).

## What's new

Version 1.6 adds streaming speech to text, the voice agent event stream with eager end of
turn, a text to speech speaker, and options to size the engine for its machine.
[CHANGELOG.md](../CHANGELOG.md) lists them, and the [guides](../README.md#guides)
describe each one. None of them changes code you already have beyond the steps and
changes above.
