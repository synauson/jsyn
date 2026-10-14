# WebRTC calls

A WebRTC participant connects a browser, or any other WebRTC peer, to a conference. The
peer sends an SDP offer, jsyn answers, and the two sides trade ICE candidates. You carry
the offer, the answer and the candidates over your own signaling channel, such as a
WebSocket. Audio is Opus: an offer without Opus audio, or whose audio section has no
direction (`a=sendrecv` and the like), makes the add throw `InvalidArgumentException`
(since natives 1.7.1). No DTMF is negotiated over WebRTC.

- [Join a browser](#join-a-browser)
- [STUN, TURN and ports](#stun-turn-and-ports)
- [Per-call options](#per-call-options)
- [Call quality](#call-quality)

## Join a browser

<!-- snippet: webrtc-join -->
```java
/** The browser sent its SDP offer: add it, answer, then trickle ICE both ways. */
static Subscription join(Conference conf, String browserId, String sdpOffer, Signaling browser) {
    WebRtcParticipantHandle peer = conf.addWebRtcParticipant(WebRtcParticipantSpec.builder()
        .participantId(browserId)
        .sdpOffer(sdpOffer)
        .build());
    browser.sendAnswer(peer.sdpAnswer());

    // The engine's ICE candidates, to the browser. Candidates found before you
    // subscribe are kept and delivered, so subscribing after the add loses none.
    return conf.streamWebRtcIceCandidates(browserId, event -> {
        if (!event.endOfCandidates) {
            browser.sendCandidate(event.candidate, event.sdpMLineIndex);
        }
    });
}

/** The browser sent one of its ICE candidates. */
static void onBrowserCandidate(WebRtcParticipantHandle peer, String candidate,
                               int sdpMLineIndex) {
    peer.addIceCandidate(candidate, sdpMLineIndex);
}
```

`Signaling` here stands for your channel to the browser.

- `addWebRtcParticipant` returns as soon as the answer is ready. It doesn't wait for
  the connection, which needs the answer to reach the browser first.
- The offer must carry exactly one media stream, audio. An offer that isn't SDP, has
  another stream, or that WebRTC negotiation refuses makes the add throw
  `InvalidArgumentException`.
- On the browser side, create the `RTCPeerConnection`, add the microphone track, send
  the offer, apply the answer, and pass candidates both ways. The
  [WebRTC testbed](https://github.com/synauson/examples/tree/main/java/jsyn-webrtc-testbed)
  is a complete Spring Boot and React example, including rejoining and clean shutdown.
- Route audio to and from the participant like any other
  ([Routing audio](conferences.md#routing-audio)).

## STUN, TURN and ports

### STUN

The runtime uses no STUN server unless you set `JSynConfig.Builder.webrtcStunServer`,
or `stunServer` on one participant. Without one, ICE offers only the runtime's own
addresses, which suits peers on the same machine or network, or a runtime with a public
address. Set one (for example `stun://stun.l.google.com:19302`) when peers reach the
runtime through NAT. Use the `stun://host:port` form, not the browser's `stun:host`
form. An empty string turns STUN off.

### TURN

jsyn has no default TURN server. Give each participant its relays with
`turnServers`, as `turn://user:password@host:port?transport=udp` (or `turns://`, or
`transport=tcp`). Percent-encode a `:`, `@`, `/`, `?` or `#` in the user or password;
a time-limited `timestamp:user` name is written `timestamp%3Auser`. A participant can
have at most 8 relays, and a `turn://` URI with no transport counts as two.
`iceTransportPolicy(IceTransportPolicy.RELAY)` makes the participant use relayed
candidates only, so the peer never sees the host's addresses; it needs a TURN server.

### Ports

Without a range, ICE uses any free port. On a host behind a firewall, set
one with `JSynConfig.Builder.webrtcIcePortRange(min, max)`, or `icePortRange` on one
participant, keep it clear of the SIP range, and open it to UDP and TCP
([Network and firewall](install.md#network-and-firewall)). Each concurrent
participant needs a port per local address for UDP and another for ICE over TCP.

## Per-call options

Every option on `WebRtcParticipantSpec` is optional. An unset option takes the
runtime's default from `JSynConfig`, or else the built-in default.

<!-- snippet: webrtc-options -->
```java
// Defaults for every WebRTC participant of this runtime.
JSynConfig config = JSynConfig.builder()
    .webrtcStunServer("stun://stun.example.com:3478")
    .webrtcIcePortRange(40000, 40999)
    .webrtcJitterBufferMs(100)
    .build();

// Options for one participant; anything unset takes the runtime default.
WebRtcParticipantHandle peer = conf.addWebRtcParticipant(WebRtcParticipantSpec.builder()
    .participantId("remote-agent")
    .sdpOffer(offer)
    .turnServers(List.of("turn://user:secret@turn.example.com:3478?transport=udp"))
    .iceTransportPolicy(IceTransportPolicy.RELAY)   // only relayed candidates
    .opusBitrate(32000)
    .opusInbandFec(true)
    .build());

// What the participant actually runs with, and how the call is going.
WebRtcStats stats = peer.stats();
System.out.println("ICE " + stats.iceConnectionState + ", " + stats.packetsLost + " lost");
if (stats.effectiveOptions != null) {
    System.out.println("sending Opus at " + stats.effectiveOptions.opusBitrate + " bps");
}
```

| `WebRtcParticipantSpec.Builder` | Runtime default (`JSynConfig.Builder`) | Built-in default |
|---|---|---|
| `stunServer` | `webrtcStunServer` | none (`""`) |
| `jitterBufferMs` (0 to 2000) | `webrtcJitterBufferMs` | 200 ms |
| `icePortRange` | `webrtcIcePortRange` | any port |
| `turnServers` | | none |
| `iceTransportPolicy` | | `ALL` |
| `opusBitrate` (4000 to 650000) | | 64000 bps |
| `opusDtx` | | on |
| `opusInbandFec` | | off |
| `opusPacketLossPercentage` (0 to 100) | | 10 with FEC on, else 0 |

The Opus options apply to the audio jsyn sends to the participant. A larger jitter
buffer smooths a poor network, but everyone hears this participant later by the same
amount. An option out of range makes the add throw `InvalidArgumentException`.

## Call quality

`peer.stats()` returns `WebRtcStats`: the ICE and peer connection states
(`iceConnectionState`, `connectionState`), packets and bytes in and
out, packets lost, the largest jitter (`maxJitterNs`), round-trip time (`rttMs`), and
`effectiveOptions`, the options the participant ended up with. After the participant
is removed it throws `NotFoundException`. Packets and bytes out count the audio the
engine sent the peer. With nothing routed to the peer, the engine sends Opus silence
frames on GStreamer 1.26 (Windows) but no audio at all on GStreamer 1.24 (Ubuntu
24.04's packages), so there they stay 0 until a route to the peer carries audio.
`rttMs` is 0 until the peer's first RTCP receiver report, and under half a millisecond
rounds to 0.
