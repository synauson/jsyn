# Deep WebRTC + SIP end-to-end testing for jsyn — design

Status: approved, not yet implemented.

## Context

`jsyn`'s CI now runs its full IT suite on both Linux and Windows GitHub-hosted
runners (see `docs/superpowers/specs/2026-08-05-ci-integration-testing-design.md`
and the corresponding plan), and a coverage audit of that suite found two real
gaps against the AI-model/native path's depth:

- `WebRtcParticipantIT` only exercises SDP offer/answer signaling. Its own
  docstring says: *"a full ICE + media exchange requires a second GStreamer
  peer; this test only exercises the SDP signaling half."* No real ICE
  negotiation, no real DTLS-SRTP handshake, no audio ever crosses the WebRTC
  leg.
- `SipParticipantIT` only exercises port allocation and handle lifecycle
  (*"does not require real RTP traffic"*), and `DtmfEventsIT` only exercises
  the `sendDtmf()` API contract — DTMF *reception* is explicitly out of scope
  (*"an event-arrival test requires a real RFC 4733 RTP loopback sender (out
  of scope for the unit test suite)"*). This mirrors a gap `synauson-server`'s
  own Rust tests (`sip_dtmf_smoke.rs`) already acknowledge and leave open.

By contrast, `VadDetectorIT`, `SmartTurnDetectorIT`, `RecordingParticipantIT`,
and the `NativeParticipant*` tests all push real audio through real ONNX
models via the real JNI boundary. WebRTC and SIP are the only participant
types where "the test passes" does not currently mean "real media flowed."

## Goal

Prove, in CI, on both Linux and Windows, that real audio — not just
signaling metadata — flows correctly through jsyn's WebRTC and SIP
participant paths, including DTMF in both directions. Close this gap with
the same rigor already proven for the Native/Recording/File paths, without
expanding scope into the `synauson` repo.

Explicitly not in scope: SRTP-over-SIP (plaintext RTP is the default and
sufficient), OPUS-over-SIP, mid-call codec switching, video, and any change
to `synauson`'s own Rust test suite or CI.

## Design

### Two new self-contained test-support peers (pure `jsyn`, zero `synauson` changes)

jsyn already has full trickle-ICE support
(`Conference#streamWebRtcIceCandidates`, `WebRtcParticipantHandle#addIceCandidate`),
which is what makes both peers viable without touching the sibling repo.

#### `WebRtcBrowserPeer`

A real headless Chromium instance, driven via Playwright's Java bindings
(`com.microsoft.playwright:playwright` — a normal Gradle test dependency,
bundles its own Chromium, no Node.js required). Launched with:

- `--use-fake-device-for-media-stream`
- `--use-fake-ui-for-media-stream` (auto-grants the mic permission prompt)
- `--use-file-for-fake-audio-capture=<path>` pointing at a raw-PCM
  conversion of `synauson-server/tests/fixtures/short_speech.wav`, so the
  browser's "microphone" plays real speech instead of a synthetic tone.

The peer loads a small inline page via `page.setContent(...)` containing
plain JS: `getUserMedia()`, `RTCPeerConnection`, `createOffer()`,
`onicecandidate`, and a `MediaStreamTrack` sink wired through a Web Audio
`AnalyserNode` to detect non-silent incoming audio. Java ↔ JS communication
uses Playwright's `page.exposeFunction` bridge (JS pushes ICE candidates and
audio-level samples to Java; Java calls back into JS to apply the remote
answer and remote ICE candidates).

Public Java-facing surface:

```java
String createOffer();
void applyAnswer(String sdpAnswer);
void addRemoteIceCandidate(String candidate, int sdpMLineIndex);
List<IceCandidate> drainLocalIceCandidates();
boolean receivedNonSilentAudio(Duration within);
void close();
```

**Flow:** the browser creates a *real* offer (real ICE ufrag/pwd, real DTLS
fingerprint — not the canned SDP the existing shallow test uses) →
`conf.addWebRtcParticipant(WebRtcParticipantSpec.builder().sdpOffer(offer)...)`
→ real SDP answer relayed back via `applyAnswer` → ICE trickles both
directions (`streamWebRtcIceCandidates` → `peer.addRemoteIceCandidate`, and
`peer.drainLocalIceCandidates()` → `handle.addIceCandidate`) → DTLS-SRTP
handshake completes for real inside both webrtcbin and Chromium → browser's
fake mic (real speech) flows into synauson.

Assertions: `conf.streamVadEvents` on the WebRTC participant fires a genuine
`VadEvent.SpeechStart` — proving the full receive chain (ICE, DTLS, SRTP,
Opus decode, VAD inference) works together over the actual WebRTC transport,
not just the Native participant path. Self-routing the WebRTC participant to
itself and asserting `peer.receivedNonSilentAudio()` proves the
synauson→browser send direction.

#### `SipRtpPeer`

One `DatagramSocket` bound to an ephemeral port. Plaintext RTP only (SRTP
explicitly out of scope — see Goal). All three capabilities share the same
RTP framing; DTMF just uses a different payload type on the same session.

```java
int localPort();
void sendAudioFrame(byte[] pcm20ms);       // hand-rolled PCMU/PCMA encode + RTP header, targeted at synauson's localRtpPort
void sendDtmfEvent(char digit, int durationMs); // RFC 4733 telephone-event packet, same target
List<RtpPacket> capturedPackets(Duration within); // decodes whatever arrives at this peer's own bound port
void close();
```

`RtpPacket` exposes `payloadType`, raw `payload` bytes, `sequenceNumber`,
and `timestamp` — callers decode PCMU/RFC4733 payloads as needed for
assertions.

**Inbound audio + VAD/SmartTurn on the SIP path:** `SipRtpPeer` binds port
`P`. `conf.addSipParticipant(SipParticipantSpec.builder().remoteRtpPort(P)
.vad(...).smartTurn(...)...)` returns a handle with `localRtpPort L`. The
test loop calls `peer.sendAudioFrame(pcmuEncode(chunk))` targeted at `L` for
real speech audio, and asserts `conf.streamVadEvents`/`streamSmartTurnEvents`
fire for real — the same assertion shape as `VadDetectorIT`/
`SmartTurnDetectorIT`, but driven over the SIP RTP path instead of the
Native participant path.

**Outbound audio:** self-route (or route from a `NativeParticipant`) to the
SIP participant via `ConnectionMatrix`; assert `peer.capturedPackets()`
contains plausible PCMU/PCMA frames arriving at the peer's own socket —
proving synauson's outbound RTP actually left the wire correctly.

**DTMF send (synauson → remote):** call `handle.sendDtmf('5', 100)`; assert
`peer.capturedPackets()` contains an RFC 4733 frame that decodes to event
`5` — closing the gap `DtmfEventsIT` left open (send-side was previously
proven only as "does not throw", never as "produces a correct wire frame").

**DTMF receive (remote → synauson):** call `peer.sendDtmfEvent('9', 100)`
targeted at `L`; assert `conf.streamDtmfEvents` fires a `DtmfEvent` with
digit `'9'` — this is the exact scenario both `jsyn`'s and `synauson-server`'s
own test docstrings flagged as requiring "a real RFC 4733 RTP loopback
sender (out of scope)." `SipRtpPeer` *is* that loopback sender.

### Test files

New, alongside the existing shallow tests (not replacing them — the shallow
tests remain valid, fast API-contract checks):

- `jsyn/src/test/java/com/synauson/jsyn/it/support/WebRtcBrowserPeer.java`
- `jsyn/src/test/java/com/synauson/jsyn/it/support/SipRtpPeer.java`
- `jsyn/src/test/java/com/synauson/jsyn/it/support/RtpPacket.java` (small
  value type shared by `SipRtpPeer` and its callers)
- `jsyn/src/test/java/com/synauson/jsyn/it/WebRtcMediaE2eIT.java`
- `jsyn/src/test/java/com/synauson/jsyn/it/SipMediaE2eIT.java`
- `jsyn/src/test/java/com/synauson/jsyn/it/SipDtmfE2eIT.java`

### Error handling

A missing/failed-to-launch Chromium in CI must fail the build loudly — no
`Assumptions.assumeTrue` skip. That skip pattern is legitimate for "this
developer's machine doesn't have the sibling `synauson` repo checked out";
it is not legitimate for "the browser we depend on isn't installed in CI,"
since silently skip-passing that is exactly the failure mode this whole
initiative exists to eliminate (per the original coverage audit that
triggered this design).

Real network/timing-based assertions are inherently more failure-prone than
pure API-contract checks. Use the same generous-timeout,
`CountDownLatch`/polling patterns already established in
`RealVadE2eLatencyIT` and `SmartTurnDetectorIT` (`@Timeout` in the 30–60s
range, `await(...)` with a multi-second budget, not tight polling loops).

### CI changes

Both `test-linux` and `test-windows` jobs (in `jsyn/.github/workflows/ci.yml`)
gain a `playwright install chromium` step before `./gradlew :jsyn:test`,
cached via `actions/cache` keyed on the Playwright dependency version — same
caching approach already used for the GStreamer/ORT downloads in those jobs.
No new job is added; this folds into the existing two.

### Cost

Playwright's Chromium download is roughly 100–150MB, cacheable across runs.
Expect a low-double-digit-minutes addition to each of `test-linux` and
`test-windows` on a cache miss, negligible on a cache hit. Comfortably within
the GitHub Team plan's 3,000 included minutes/month at this project's push
cadence (per the cost analysis in the original CI integration testing
design).

## Testing

Self-verifying, same loop as the prior CI integration testing effort's Task
5: faithful local reproduction via `podman run ubuntu:24.04` for the Linux
side (download real Nexus artifacts, real `synauson` checkout, real
Playwright/Chromium, run the full suite) before ever touching real CI;
Windows has no equivalent faithful local reproduction available, so that
side iterates directly against real CI runs. Execution via
subagent-driven-development, matching the prior effort.
