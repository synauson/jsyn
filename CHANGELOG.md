# Changelog

All notable changes to jsyn, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/2.0.0/). Each release names the
jsyn-natives version it needs; use that version or a newer one.

## [1.6.0] - 2026-10-11

Needs jsyn-natives 1.7.1 or newer, and a new license key. Upgrading from 1.5 takes a
few code changes: follow [Upgrading from jsyn 1.5 to 1.6](https://github.com/synauson/jsyn/blob/v1.6.0/docs/upgrading.md).

### Added

- Streaming speech to text: `SttConfig` on a participant, read with
  `Conference.streamTranscriptEvents` (`TranscriptEvent.Delta` and `Turn`). See
  [Speech to text](https://github.com/synauson/jsyn/blob/v1.6.0/docs/speech-to-text.md).
- The voice agent event stream, `Conference.streamAgentEvents`: speech activity, turns
  with their reason and timed words, and the speaker's playback, in one ordered stream
  per participant that resumes after a lag without losing events
  (`AgentEvent`, `AgentStreamOptions`, `AgentStreamException`). See
  [Voice agents](https://github.com/synauson/jsyn/blob/v1.6.0/docs/voice-agents.md).
- Turn control during a call: `Conference.forceEndTurn` and `updateTurnConfig`, and
  `TurnDetectionConfig.withTurns` to start with your own turn config.
- Eager end of turn: `EagerEndOfTurn` and `TurnResumed` events, turned on with
  `TurnConfigUpdate.withEager(true)` and, with speech to text,
  `JSynConfig.Builder.sttEager(true)`.
- Text to speech: `TtsConfig` gives a participant a speaker; `Conference.speak`,
  `cancelUtterance` and `cancelUtterances` drive it, and the agent stream reports each
  word played. 20 American English voices, `en-us-f1` to `en-us-f11` and `en-us-m1` to
  `en-us-m9`. See [Text to speech](https://github.com/synauson/jsyn/blob/v1.6.0/docs/text-to-speech.md).
- Sizing options on `JSynConfig.Builder`: `sttCapacity`, `ttsCapacity`, `cpuBudget`,
  `memoryBudget`, `recalibrate` and `sttTurnFlush`. See
  [Capacity and sizing](https://github.com/synauson/jsyn/blob/v1.6.0/docs/sizing.md).
- More in `JSyn.capabilities()`: the license plan, days remaining and problems, the
  session pool, the entitlement that grants each capability, speech to text and text to
  speech capacity, the CPU and memory the engine detected, its timings, and a
  `downloading` model state.
- One session pool for all your runtimes and servers under a license, at every site.
  Each runtime reports its session counts to `usage.synauson.com` and gets a share of
  the pool, with the burst on its purchased part; `JSyn.capabilities().usageService`
  shows the share, the license-wide count and how long the runtime can go without
  contact, and `limitsScope` reads `all instances of this license`. Calls in progress
  are never stopped, and a runtime that can't reach the service keeps its last share
  for 7 days (paused while the outage is on Synauson's side).
- `JSyn.modelNotices` returns each installed model's license notice.
- `JSyn.shutdownServer(Duration)` ends every conference with the grace period you
  give.
- Guides for every feature under [docs/](https://github.com/synauson/jsyn/tree/v1.6.0/docs), an installation guide for Linux and
  Windows, and code samples that are compiled before every release.

### Changed

These changes need action when you upgrade from 1.5:

- License keys issued for jsyn 1.5 and earlier don't work with natives 1.7 and newer.
  Replace `SYNAUSON_LICENSE_KEY` (or `JSynConfig.licenseKey`) with the new key you
  receive from Synauson.
- The turn-detection API is named by its role: `TurnDetectionConfig`,
  `TurnDetectionEvent`, `Conference.streamTurnDetectionEvents` and the specs'
  `turnDetection(...)` option replace the 1.5 names, which carried the model's name.
- A participant with turn detection needs VAD on the same spec. Without it, adding the
  participant throws `InvalidArgumentException`; in 1.5 it was accepted and produced no
  turn events.
- Licenses have one pool of concurrent AI sessions, where they counted conferences and
  streams before. `Capabilities.conferences`,
  `aiConferences` and `CapabilityInfo.streams` are now null, and `license.state` has
  new values.
- Every runtime needs HTTPS to `usage.synauson.com`, where it reports its AI session
  counts every 30 seconds (counts only, no call content) and renews its license.
  Hosts that can never reach Synauson need an air-gapped license from Synauson, set
  with `licenseFile`.
- Also allow HTTPS to `license.synauson.com` (model downloads, and a start without a
  cached license file) and `status.synauson.com` (Synauson's outage notices), keep the
  clock synchronised with NTP, and keep `stateDir` on storage that survives restarts.
- `new JSyn` needs a license file that still works: 7 days after the last usage report
  that got through. Without one (offline, or `usage.synauson.com` unreachable) it throws
  `PermissionDeniedException`; the free floor (detect, 4 sessions) is gone.
- A running runtime whose reports don't get through is `offline` from the first hour
  and `disconnected` after 7 days, on any plan: no new conference or participant starts
  until a report gets through. Calls in progress continue.
- No feature tiers: every license has every AI feature on every session, and
  `license.plan` is `all` (`none` while the license starts nothing new). Licenses differ
  in their session pool: the Free base of 2 plus the sessions they buy, with the burst
  on the bought ones only. `Capabilities.Sessions` gains `base` and `purchased`.
- A Free key gets the Free plan: 2 sessions with every AI feature, no burst, for
  production, with no expiry. A license falls to it when it expires (`expired`), and a
  runtime that is offline when its paid plan expires admits no new AI sessions until it
  reconnects. A license warns 14 days before it expires (`expiring`); the 14 days of
  grace are gone.
- `JSynConfig.webrtcStunServer` defaults to none (`""`), not Google's public STUN
  server. Set one when WebRTC peers reach the runtime through NAT.
- Mistakes in a request throw `AlreadyExistsException` (an id already in use),
  `InvalidArgumentException` (a WebRTC offer that isn't SDP with one audio stream, a
  file that can't be played) or `NotFoundException` (a missing file, or an unknown
  participant in a route, a DTMF or detector stream subscription), not
  `InternalException`. `addPriorityAudioFiles` on a participant that isn't a file
  participant throws `FailedPreconditionException`. `InternalException` now means the
  engine itself failed.
- File participants play WAV (PCM, A-law or mu-law), Ogg Opus, Ogg Vorbis, FLAC and
  MP3 only. Any other format (AAC or M4A, for one) throws `InvalidArgumentException`
  naming what the file is, where it used to play if the machine had a decoder for it.
  A priority file in another format sends `FileEvent.FileError` and is skipped.
- `VadEvent.SpeechEnd.durationMs` is the length of the speech that ended. It used to
  be the silence that confirmed the end, about `minSilenceMs`.
- `TurnDetectionConfig`'s `bufferedSamples` takes effect: it sets how much audio a
  participant must have sent before turn detection decides. Values from 0 to 4000
  (250 ms) mean 4000, and a negative value or more than 128000 throws
  `InvalidArgumentException`.
- Models have new ids, `sentito-1` (VAD), `fermata-1` (turn detection), `spartito-1`
  (speech to text) and `lettura-2` (text to speech), and a new store layout. The first
  start downloads them again.
- `https://maven.synauson.com/releases` serves jsyn 1.6.0 and natives 1.7.1 onwards;
  older versions are no longer published there.

And these don't:

- Model downloads continue after startup: a failed download retries, a license upgrade
  downloads the newly included models without a restart, and runtimes sharing a store
  download each model once.
- The engine refuses settings it doesn't recognise in a config or spec, where it used
  to ignore them, so a jsyn and natives that don't match fail at once.
- Models download from Cloudflare R2: allow HTTPS to `*.r2.cloudflarestorage.com`.
- `muteParticipant` mutes native participants too, and a route added while a
  participant is muted starts muted.
- A file that can't be played sends `FileEvent.FileError` with the reason, at the add
  (which throws too) or while it plays. A priority file that fails is skipped.
- Closing a runtime while speech to text or text to speech is still loading waits for
  the load to stop, which can take a few seconds.
- A panic in the engine's native code no longer ends the JVM: it is logged, and the call
  throws `InternalException`.
- On Windows, the engine turns off ONNX Runtime's telemetry when it loads it. The library
  still writes a few events to Windows' local event tracing at startup, before it can be
  turned off; the engine sends none of them anywhere.
- The natives are licensed under the
  [Synauson End User License Agreement](https://synauson.com/legal/eula/), version 1.11,
  which each natives jar carries as `META-INF/LICENSE`.

### Deprecated

- `Capabilities.conferences`, `Capabilities.aiConferences` and
  `CapabilityInfo.streams`. Read `Capabilities.sessions`.

### Fixed

- On Windows, every JVM left its extracted natives (about 25 MB) in a `jsyn-natives-*`
  folder under the temp directory, because Windows can't delete a loaded DLL at exit.
  Loading jsyn now deletes such folders older than an hour that no running process uses.
- A SIP caller, native participant or recording added to a conference that had run for
  a while got the conference's whole age as silence at once. They now start in real
  time, and a recording is as long as it ran.
- After a file participant that doesn't loop finished, a SIP caller it was routed to got
  thousands of RTP packets a second, and a native participant minutes of audio a second,
  until the file participant was removed.
- A file added to a running conference played its first seconds at once. Files now
  play in real time from the moment they are added, looping files and priority audio
  included.
- A priority file over a file participant that had played for a while played only its
  last half second or so. It now plays whole, queued files play in turn, and the
  participant's own file resumes where it paused.
- Each time a looping file rewound, timing jumped for the whole conference, which
  could leave gaps in other audio.
- `JSyn.shutdownServer()` gave the conferences no grace period. It now gives them five
  seconds.
- Closing a `Conference` that `shutdownServer` already ended no longer throws
  `NotFoundException`.
- `TurnDetectionConfig.defaults()` sets `bufferedSamples` to 4000 (250 ms), the value
  the engine applies. It set 160, which the engine raises to 4000.
- `WebRtcParticipant.stats()` counted 0 packets, bytes, losses, jitter and round-trip
  time. It now reports them.
- Closing `JSyn` with conferences still open left their recordings as unfinished WAV
  files. It now ends those conferences first, waiting up to five seconds.
- A WebRTC offer without Opus audio was answered as if it worked, with no audio from the
  engine. It now throws `InvalidArgumentException`, as does an offer whose audio has no
  direction.
- RFC 4733 events on an Opus SIP call were read at 8 kHz, which upset the call's audio
  timing on every digit. They now run on the codec's 48 kHz clock: negotiate
  `telephone-event/48000` with Opus.
- When a WebRTC caller's audio stopped in the middle of speech, the turn never ended. It
  now ends as it does on a SIP call.
- A SIP participant sent its RTCP from a random port instead of the one after its RTP
  port.
- On a busy machine, adding a participant, recording, route or speaker could fail with
  a timeout while the engine started its audio.
- When adding a route between two participants failed partway, every later attempt to
  route the same two participants failed too.
- Creating two `JSyn` runtimes at the same time in one process could crash it.
- If the engine couldn't apply its own answer to a WebRTC offer, `addWebRtcParticipant`
  still returned the answer, with no call behind it. It now throws with the reason.
- On a file participant that loops, a turn could never end. Turns on looping and
  priority files now close.
- Every participant with turn detection logged a spurious `propose_allocation failed`
  error.
