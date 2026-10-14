# SIP calls

jsyn handles the media of a SIP call: an RTP session with the far end, optionally
encrypted with SRTP, carrying G.711 or Opus audio and DTMF. Your SIP stack (a
proxy, a B2BUA, or a SIP library in your application) handles the signaling and the
SDP. You read the peer's address, port and codec from its SDP, and put the port jsyn
gives you in yours.

- [Answer an inbound call](#answer-an-inbound-call)
- [Place an outbound call](#place-an-outbound-call)
- [SRTP](#srtp)
- [DTMF](#dtmf)
- [Call quality](#call-quality)
- [Ports and firewalls](#ports-and-firewalls)

## Answer an inbound call

When the peer's media is already known, as with an inbound call's offer, add the
participant in one step:

<!-- snippet: sip-inbound -->
```java
// The caller's SDP offer gave its address, RTP port and codec.
SipParticipantHandle caller = conf.addSipParticipant(SipParticipantSpec.builder()
    .participantId("caller")
    .remoteIp(peerIp)
    .remoteRtpPort(peerRtpPort)
    .codec("PCMU")                   // PCMU, PCMA or OPUS
    .dtmfPayloadType(101)            // RFC 4733 events; 0 sends digits as tones
    .vad(VadConfig.defaults())       // optional detectors, as on any participant
    .turnDetection(TurnDetectionConfig.defaults())
    .build());

// Put this port in your SDP answer.
int localPort = caller.localRtpPort();
```

`addSipParticipant` returns once the participant is ready to receive RTP.
`localRtpPort()` is the port it receives on, from the runtime's SIP range; RTCP uses
the next port up. The participant sends from the port it receives on (symmetric RTP).
A codec other than `PCMU`, `PCMA` or `OPUS` throws `InvalidArgumentException`.

Then route audio to and from the caller ([Routing audio](conferences.md#routing-audio)).

## Place an outbound call

For an outbound call your offer must name a local port before you know the peer's
media. Reserve the ports first, then connect when the answer arrives:

<!-- snippet: sip-outbound -->
```java
// 1. Reserve ports before you send the offer.
SipReservation reservation = conf.reserveSipParticipant(SipReservationSpec.builder()
    .participantId("callee")
    .build());
int offerPort = reservation.localRtpPort(); // goes in the offer's m= line

// 2. When the answer arrives, connect with the media it negotiated.
SipParticipantHandle callee = conf.connectSipParticipant(SipConnectionSpec.builder()
    .participantId("callee")
    .remote(SipRemoteMedia.builder()
        .remoteIp(answerIp)
        .remoteRtpPort(answerPort)
        .codec("PCMA")
        .dtmfPayloadType(101)
        .build())
    .build());

// If the call fails before step 2, release the ports.
// conf.removeParticipant("callee");
```

- Both ports are bound as soon as the reservation returns, so media the peer sends
  early waits in the socket's receive buffer instead of hitting a closed port. Nothing
  plays or records before the connect.
- A reservation isn't a participant yet: it isn't in `conf.state()`. It lasts until you
  connect it, remove it with `removeParticipant`, or the conference ends.
- `SipConnectionSpec` takes the same `vad`, `turnDetection`, `stt` and `tts` options as
  `SipParticipantSpec`.
- If the connect fails on its arguments (an SRTP key on one side only, say), or because
  a model one of its detectors needs isn't installed, the reservation stays and you can
  retry. Any other failure releases it.

## SRTP

jsyn supports SRTP with SDES keys (`AES_CM_128_HMAC_SHA1_80`): 30-byte master keys, a
16-byte key and a 14-byte salt, as carried in the SDP `a=crypto` lines.

<!-- snippet: sip-srtp -->
```java
// Inbound: both 30-byte keys (AES_CM_128_HMAC_SHA1_80) from the SDP a=crypto lines.
conf.addSipParticipant(SipParticipantSpec.builder()
    .participantId("secure-caller")
    .remoteIp("192.0.2.10")
    .remoteRtpPort(40000)
    .codec("PCMU")
    .srtp(new SrtpConfig(ourKey, theirKey))
    .build());

// Outbound: our key at reservation, the peer's key at connect.
conf.reserveSipParticipant(SipReservationSpec.builder()
    .participantId("secure-callee")
    .ourSrtpKey(ourKey)
    .build());
conf.connectSipParticipant(SipConnectionSpec.builder()
    .participantId("secure-callee")
    .remote(SipRemoteMedia.builder()
        .remoteIp("192.0.2.20").remoteRtpPort(40002).codec("PCMU")
        .srtpKey(theirKey)
        .build())
    .build());
```

Our key encrypts what we send, and the peer's key decrypts what it sends. A key of
another length throws `InvalidArgumentException`. On an outbound call, give the peer's
key at connect if, and only if, you gave our key at reservation.

## DTMF

<!-- snippet: dtmf -->
```java
// Receive digits the caller presses.
Subscription digits = conf.streamDtmfEvents("caller", event ->
    System.out.println("pressed " + event.digit + (event.inBand ? " (tone)" : " (RFC 4733)")));

// Send a digit to the caller: 0-9, *, #, A-D, for 160 ms.
caller.sendDtmf('5', 160);

// Call quality, read whenever you like.
SipStats stats = caller.stats();
System.out.println(stats.packetsReceived + " packets in, " + stats.packetsLost + " lost");
```

- With a DTMF payload type set (96 to 127, from the SDP's `telephone-event` line),
  `sendDtmf` sends RFC 4733 events. With `dtmfPayloadType(0)` it plays the digit as
  tones in the audio instead.
- RFC 4733 events run on the codec's RTP clock, as they share the audio's stream:
  negotiate `telephone-event/48000` with Opus and `telephone-event/8000` with PCMU or
  PCMA. Natives 1.7.0 and older read Opus calls' events at 8 kHz.
- Received digits arrive as `DtmfEvent`s, from RFC 4733 events (`inBand` false) or
  detected in the audio (`inBand` true). `durationMs` is always 0 for received digits.
- `sendDtmf` takes a duration in milliseconds, which the engine keeps between 70 and
  500. An invalid digit throws `InvalidArgumentException`.

## Call quality

`handle.stats()` returns `SipStats`: packets received and lost, average jitter
(`avgJitterNs`) and the number of active SSRCs. After the
participant is removed it throws `NotFoundException`.

## Ports and firewalls

Each SIP participant uses an RTP and RTCP port pair from the runtime's range,
`rtpPortMin` to `rtpPortMax` (10000 to 20000 by default). Ports are bound exclusively,
so two runtimes on one host never share a pair. A failed add releases its ports. Open
the range to inbound UDP for the Java process
([Network and firewall](install.md#network-and-firewall)). The receive jitter buffer is
`rtpJitterBufferMs`, 200 ms by default.
