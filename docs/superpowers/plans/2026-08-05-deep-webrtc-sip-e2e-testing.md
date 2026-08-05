# Deep WebRTC + SIP End-to-End Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove, in CI on both Linux and Windows, that real audio (not just signaling) flows through jsyn's WebRTC and SIP participant paths, including bidirectional DTMF, by adding real network/browser test peers alongside the existing shallow API-contract tests.

**Architecture:** Two new self-contained test-support peers live entirely in `jsyn` (zero changes to the sibling `synauson` repo): `SipRtpPeer` (a plain Java `DatagramSocket` speaking real RTP/PCMU + RFC 4733 DTMF) and `WebRtcBrowserPeer` (a real headless Chromium instance driven by Playwright-Java, doing real ICE/DTLS-SRTP/Opus). New IT tests use these peers to assert real `VadEvent`/`DtmfEvent` firing and real packets on the wire — supplementing, not replacing, the existing signaling/API-contract tests.

**Tech Stack:** Java 11, JUnit 5, `java.net.DatagramSocket`, `com.sun.net.httpserver.HttpServer` (JDK built-in), Playwright-Java (`com.microsoft.playwright:playwright`), Gradle, GitHub Actions (`ubuntu-24.04`, `windows-2022`).

---

## Context for every task

All new test-support classes live in a new package, `com.synauson.jsyn.it.support`, under `jsyn/src/test/java/com/synauson/jsyn/it/support/`. They must be `public` (referenced from `com.synauson.jsyn.it.*IT` test classes in the parent package). All new IT test classes are added **alongside** the existing `WebRtcParticipantIT`, `SipParticipantIT`, and `DtmfEventsIT` — do not delete or modify those three files; they remain valid, fast, signaling/API-contract checks.

`JSynTestHelpers.resolveSynausonRepo()` (already exists, `jsyn/src/test/java/com/synauson/jsyn/it/JSynTestHelpers.java`) resolves the sibling `synauson` checkout for fixtures. `JSynTestHelpers.newJSyn()` returns a `JSyn` instance with a unique RTP port range already wired to `resolveSynausonRepo().resolve("models")`.

**Java 11 constraint:** jsyn compiles against Java 11 (`JavaLanguageVersion.of(11)` in `jsyn/build.gradle.kts`, `actions/setup-java@v4` with `java-version: '11'` in CI). Do **not** use text blocks (`"""..."""`, Java 15+), records (Java 16+), pattern-matching `instanceof` with a bound variable (Java 16+), or switch expressions (Java 14+). The existing `WebRtcParticipantIT.MINIMAL_OPUS_OFFER` already establishes the pattern for multi-line string literals under Java 11: `String.join("\n", "line1", "line2", ...)`. Follow that pattern for all new multi-line strings.

---

### Task 1: `RtpPacket` value type

**Files:**
- Create: `jsyn/src/test/java/com/synauson/jsyn/it/support/RtpPacket.java`
- Test: `jsyn/src/test/java/com/synauson/jsyn/it/support/RtpPacketTest.java`

`RtpPacket` is a plain RFC 3550 RTP packet parser/builder (no CSRC list, no header extension support — not needed for this test suite). Both `SipRtpPeer` (Task 2) and the SIP IT tests (Tasks 4–5) depend on it.

- [ ] **Step 1: Write the failing test**

```java
package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RtpPacketTest {

    @Test
    void buildAndParseRoundTrips() {
        byte[] payload = {1, 2, 3, 4};
        byte[] wire = RtpPacket.build(0, true, 1234, 96000L, 0xCAFEBABEL, payload);

        RtpPacket parsed = RtpPacket.parse(wire, wire.length);

        assertEquals(0, parsed.payloadType);
        assertEquals(1234, parsed.sequenceNumber);
        assertEquals(96000L, parsed.timestamp);
        assertEquals(0xCAFEBABEL, parsed.ssrc);
        assertArrayEquals(payload, parsed.payload);
    }

    @Test
    void markerBitIsEncodedSeparatelyFromPayloadType() {
        byte[] noMarker = RtpPacket.build(101, false, 1, 0L, 1L, new byte[]{9});
        byte[] withMarker = RtpPacket.build(101, true, 1, 0L, 1L, new byte[]{9});

        assertEquals(101, RtpPacket.parse(noMarker, noMarker.length).payloadType);
        assertEquals(101, RtpPacket.parse(withMarker, withMarker.length).payloadType);
        // Marker bit lives in byte[1] bit 7; payload type must not be corrupted by it.
        assertEquals((byte) 0x65, noMarker[1]);       // 0110 0101 = marker 0, PT 101
        assertEquals((byte) 0xE5, withMarker[1]);     // 1110 0101 = marker 1, PT 101
    }

    @Test
    void parseRejectsPacketShorterThanRtpHeader() {
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.parse(new byte[]{1, 2, 3}, 3));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.support.RtpPacketTest' --no-daemon --console=plain`
Expected: FAIL to compile — `RtpPacket` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.synauson.jsyn.it.support;

/**
 * A single RTP packet (RFC 3550, fixed 12-byte header, no CSRC list, no
 * header extension). Used by {@link SipRtpPeer} to build outgoing packets
 * and decode whatever synauson sends back.
 */
public final class RtpPacket {
    public final int payloadType;
    public final int sequenceNumber;
    public final long timestamp;
    public final long ssrc;
    public final byte[] payload;

    public RtpPacket(int payloadType, int sequenceNumber, long timestamp, long ssrc, byte[] payload) {
        this.payloadType = payloadType;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.ssrc = ssrc;
        this.payload = payload;
    }

    /** Parse a raw UDP datagram as an RTP packet. */
    public static RtpPacket parse(byte[] buf, int len) {
        if (len < 12) {
            throw new IllegalArgumentException("packet too short to be RTP: " + len + " bytes");
        }
        int payloadType = buf[1] & 0x7F;
        int sequenceNumber = ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
        long timestamp = ((long) (buf[4] & 0xFF) << 24) | ((long) (buf[5] & 0xFF) << 16)
                | ((long) (buf[6] & 0xFF) << 8) | (buf[7] & 0xFF);
        long ssrc = ((long) (buf[8] & 0xFF) << 24) | ((long) (buf[9] & 0xFF) << 16)
                | ((long) (buf[10] & 0xFF) << 8) | (buf[11] & 0xFF);
        byte[] payload = new byte[len - 12];
        System.arraycopy(buf, 12, payload, 0, payload.length);
        return new RtpPacket(payloadType, sequenceNumber, timestamp, ssrc, payload);
    }

    /** Serialize an RTP header + payload ready to send over a socket. */
    public static byte[] build(int payloadType, boolean marker, int sequenceNumber,
                                long timestamp, long ssrc, byte[] payload) {
        byte[] out = new byte[12 + payload.length];
        out[0] = (byte) 0x80; // version=2, padding=0, extension=0, CSRC count=0
        out[1] = (byte) ((marker ? 0x80 : 0x00) | (payloadType & 0x7F));
        out[2] = (byte) ((sequenceNumber >> 8) & 0xFF);
        out[3] = (byte) (sequenceNumber & 0xFF);
        out[4] = (byte) ((timestamp >> 24) & 0xFF);
        out[5] = (byte) ((timestamp >> 16) & 0xFF);
        out[6] = (byte) ((timestamp >> 8) & 0xFF);
        out[7] = (byte) (timestamp & 0xFF);
        out[8] = (byte) ((ssrc >> 24) & 0xFF);
        out[9] = (byte) ((ssrc >> 16) & 0xFF);
        out[10] = (byte) ((ssrc >> 8) & 0xFF);
        out[11] = (byte) (ssrc & 0xFF);
        System.arraycopy(payload, 0, out, 12, payload.length);
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.support.RtpPacketTest' --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
cd jsyn
git add src/test/java/com/synauson/jsyn/it/support/RtpPacket.java \
        src/test/java/com/synauson/jsyn/it/support/RtpPacketTest.java
git commit -S -m "add RtpPacket value type for deep SIP RTP testing

Why: SipRtpPeer (next task) needs to build and parse real RTP packets to
send/receive real audio and RFC 4733 DTMF over a plain UDP socket, closing
the gap SipParticipantIT/DtmfEventsIT explicitly punt on (no real RTP
traffic, DTMF reception out of scope)."
```

---

### Task 2: `MuLawCodec`

**Files:**
- Create: `jsyn/src/test/java/com/synauson/jsyn/it/support/MuLawCodec.java`
- Test: `jsyn/src/test/java/com/synauson/jsyn/it/support/MuLawCodecTest.java`

Package-private ITU-T G.711 μ-law codec (RFC 3551 static payload type 0, `"PCMU"` — the default codec every existing SIP IT test already uses). Only `SipRtpPeer` needs it directly, so it does not need to be `public`.

- [ ] **Step 1: Write the failing test**

```java
package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuLawCodecTest {

    @Test
    void silenceEncodesAndDecodesNearZero() {
        byte encoded = MuLawCodec.encode((short) 0);
        short decoded = MuLawCodec.decode(encoded);
        assertTrue(Math.abs(decoded) < 50, "silence should decode near zero, got " + decoded);
    }

    @Test
    void roundTripPreservesApproximateAmplitude() {
        short original = 10000;
        byte encoded = MuLawCodec.encode(original);
        short decoded = MuLawCodec.decode(encoded);
        // mu-law is lossy (8-bit logarithmic encoding of a 16-bit sample);
        // this tolerance matches the codec's known worst-case quantization
        // error at this amplitude range.
        assertTrue(Math.abs(decoded - original) < 500,
                "mu-law round trip should stay within lossy-compression tolerance, got " + decoded);
    }

    @Test
    void encodeBufferProducesOneByytePerSample() {
        byte[] pcm = new byte[320]; // 160 samples * 2 bytes
        for (int i = 0; i < 160; i++) {
            short s = (short) (10000 * Math.sin(2 * Math.PI * i / 40.0));
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        byte[] encoded = MuLawCodec.encodeBuffer(pcm, 0, pcm.length);

        assertEquals(160, encoded.length);
    }

    @Test
    void decodeBufferProducesTwoBytesPerSample() {
        byte[] ulaw = new byte[160];
        byte[] decoded = MuLawCodec.decodeBuffer(ulaw);
        assertEquals(320, decoded.length);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.support.MuLawCodecTest' --no-daemon --console=plain`
Expected: FAIL to compile — `MuLawCodec` does not exist yet.

- [ ] **Step 3: Write the implementation**

This is the standard reference μ-law encode/decode algorithm (ITU-T G.711, originally published as Sun Microsystems' `g711.c`, unchanged for decades and used identically across virtually every audio codec library).

```java
package com.synauson.jsyn.it.support;

/** ITU-T G.711 mu-law codec (RFC 3551 static payload type 0, "PCMU"). */
final class MuLawCodec {
    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;
    private static final int[] SEG_END = {0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF, 0x3FFF, 0x7FFF};
    private static final int[] EXP_LUT = {0, 132, 396, 924, 1980, 4092, 8316, 16764};

    private MuLawCodec() {}

    static byte encode(short pcm) {
        int sample = pcm;
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = -sample;
        if (sample > CLIP) sample = CLIP;
        sample += BIAS;
        int exponent = 7;
        for (int i = 0; i < SEG_END.length; i++) {
            if (sample <= SEG_END[i]) {
                exponent = i;
                break;
            }
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        int ulawByte = ~(sign | (exponent << 4) | mantissa);
        return (byte) ulawByte;
    }

    static short decode(byte ulawByte) {
        int u = ~ulawByte & 0xFF;
        int sign = u & 0x80;
        int exponent = (u >> 4) & 0x07;
        int mantissa = u & 0x0F;
        int sample = EXP_LUT[exponent] + (mantissa << (exponent + 3));
        if (sign != 0) sample = -sample;
        return (short) sample;
    }

    /** Encode {@code length} bytes of signed 16-bit little-endian PCM starting at {@code offset}. */
    static byte[] encodeBuffer(byte[] pcmS16LE, int offset, int length) {
        int frames = length / 2;
        byte[] out = new byte[frames];
        for (int i = 0; i < frames; i++) {
            int lo = pcmS16LE[offset + i * 2] & 0xFF;
            int hi = pcmS16LE[offset + i * 2 + 1];
            short sample = (short) ((hi << 8) | lo);
            out[i] = encode(sample);
        }
        return out;
    }

    /** Decode mu-law bytes back to signed 16-bit little-endian PCM. */
    static byte[] decodeBuffer(byte[] ulaw) {
        byte[] out = new byte[ulaw.length * 2];
        for (int i = 0; i < ulaw.length; i++) {
            short sample = decode(ulaw[i]);
            out[i * 2] = (byte) (sample & 0xFF);
            out[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.support.MuLawCodecTest' --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
cd jsyn
git add src/test/java/com/synauson/jsyn/it/support/MuLawCodec.java \
        src/test/java/com/synauson/jsyn/it/support/MuLawCodecTest.java
git commit -S -m "add MuLawCodec for real PCMU audio in SIP e2e tests

Why: SipRtpPeer needs to encode real speech audio as PCMU to prove VAD
fires on the SIP path from genuine RTP traffic, and decode captured PCMU
to prove synauson's outbound audio is real, not silence."
```

---

### Task 3: `SipRtpPeer`

**Files:**
- Create: `jsyn/src/test/java/com/synauson/jsyn/it/support/SipRtpPeer.java`
- Test: `jsyn/src/test/java/com/synauson/jsyn/it/support/SipRtpPeerTest.java`

`SipRtpPeer` is a real UDP socket that acts as the "remote SIP endpoint" in tests: it sends real PCMU-encoded audio and real RFC 4733 DTMF events to synauson's SIP participant, and captures whatever synauson sends back. Construction and target-setting are split into two steps because of an unavoidable ordering constraint: the peer's local port must be known *before* calling `addSipParticipant` (it's passed as `remoteRtpPort`), but synauson's local port — the peer's send target — is only known *after* `addSipParticipant` returns.

- [ ] **Step 1: Write the failing test**

This test only exercises `SipRtpPeer` against itself (two peers looping packets to each other) — no `synauson` JNI/model dependency, so it can run in total isolation and fast.

```java
package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
class SipRtpPeerTest {

    @Test
    void sendAudioFrameRejectsWrongSizedFrame() throws Exception {
        try (SipRtpPeer peer = new SipRtpPeer()) {
            peer.setTarget("127.0.0.1", peer.localPort());
            assertThrows(IllegalArgumentException.class, () -> peer.sendAudioFrame(new byte[100]));
        }
    }

    @Test
    void sendBeforeSetTargetThrows() throws Exception {
        try (SipRtpPeer peer = new SipRtpPeer()) {
            assertThrows(IllegalStateException.class, () -> peer.sendAudioFrame(new byte[320]));
        }
    }

    @Test
    void twoPeersCanExchangeAudioFrames() throws Exception {
        try (SipRtpPeer a = new SipRtpPeer();
             SipRtpPeer b = new SipRtpPeer()) {
            a.setTarget("127.0.0.1", b.localPort());

            byte[] frame = new byte[320];
            for (int i = 0; i < 320; i++) frame[i] = (byte) i;
            a.sendAudioFrame(frame);

            long deadline = System.currentTimeMillis() + 5000;
            List<RtpPacket> captured = List.of();
            while (System.currentTimeMillis() < deadline && captured.isEmpty()) {
                captured = b.capturedPackets();
                if (captured.isEmpty()) Thread.sleep(50);
            }

            assertFalse(captured.isEmpty(), "peer b should have captured the frame peer a sent");
            assertEquals(0, captured.get(0).payloadType, "PCMU payload type is 0");
        }
    }

    @Test
    void sendDtmfEventFirstPacketCarriesMarkerBit() throws Exception {
        try (SipRtpPeer a = new SipRtpPeer();
             SipRtpPeer b = new SipRtpPeer()) {
            a.setTarget("127.0.0.1", b.localPort());

            a.sendDtmfEvent(5, 150, 101);

            long deadline = System.currentTimeMillis() + 5000;
            List<RtpPacket> captured = List.of();
            while (System.currentTimeMillis() < deadline && captured.size() < 2) {
                captured = b.capturedPackets();
                if (captured.size() < 2) Thread.sleep(50);
            }

            assertTrue(captured.size() >= 2, "a real DTMF event sends multiple packets");
            for (RtpPacket p : captured) {
                assertEquals(101, p.payloadType);
                assertEquals(5, p.payload[0] & 0xFF, "event number must be 5");
            }
            // Last packet must have the end-of-event bit (0x80) set in payload byte 1.
            RtpPacket last = captured.get(captured.size() - 1);
            assertTrue((last.payload[1] & 0x80) != 0, "final packet must set the end-of-event bit");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.support.SipRtpPeerTest' --no-daemon --console=plain`
Expected: FAIL to compile — `SipRtpPeer` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.synauson.jsyn.it.support;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal real SIP/RTP remote peer for integration tests.
 *
 * <p>Sends real mu-law-encoded RTP audio and real RFC 4733 DTMF event
 * packets to a synauson SIP participant's local RTP port, and captures
 * whatever synauson sends back to this peer's own bound port. Runs a
 * background receive loop so packets are captured continuously, not just
 * during an explicit blocking read.
 *
 * <p>Construction is split from target-setting: {@link #localPort()} must
 * be known before calling {@code Conference#addSipParticipant} (it becomes
 * {@code remoteRtpPort}), but synauson's own local port — this peer's send
 * target — is only known from the returned handle, so callers must call
 * {@link #setTarget} after {@code addSipParticipant} returns and before
 * sending anything.
 */
public final class SipRtpPeer implements AutoCloseable {
    private final DatagramSocket socket;
    private final long ssrc;
    private final AtomicInteger audioSequenceNumber = new AtomicInteger(1000);
    private final AtomicInteger dtmfSequenceNumber = new AtomicInteger(5000);
    private final List<RtpPacket> captured = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread receiveThread;

    private volatile InetAddress targetAddress;
    private volatile int targetPort = -1;

    public SipRtpPeer() throws IOException {
        this.socket = new DatagramSocket(0);
        this.socket.setSoTimeout(200);
        this.ssrc = new Random().nextInt(Integer.MAX_VALUE);
        this.receiveThread = new Thread(this::receiveLoop, "sip-rtp-peer-recv-" + socket.getLocalPort());
        this.receiveThread.setDaemon(true);
        this.receiveThread.start();
    }

    /** The local port this peer is bound to; pass as {@code remoteRtpPort} to {@code SipParticipantSpec}. */
    public int localPort() {
        return socket.getLocalPort();
    }

    /** Set where {@link #sendAudioFrame} / {@link #sendDtmfEvent} deliver packets to. */
    public void setTarget(String host, int port) throws IOException {
        this.targetAddress = InetAddress.getByName(host);
        this.targetPort = port;
    }

    private void receiveLoop() {
        byte[] buf = new byte[2048];
        while (running.get()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                captured.add(RtpPacket.parse(packet.getData(), packet.getLength()));
            } catch (SocketTimeoutException expected) {
                // normal: re-check the running flag and loop again
            } catch (IOException e) {
                if (running.get()) {
                    throw new RuntimeException("SipRtpPeer receive loop failed", e);
                }
            }
        }
    }

    /**
     * Send one 20ms mu-law-encoded RTP audio frame (160 samples at 8kHz) to
     * synauson's local RTP port.
     *
     * @param pcmS16LE160Samples exactly 320 bytes (160 signed 16-bit LE samples)
     */
    public void sendAudioFrame(byte[] pcmS16LE160Samples) throws IOException {
        if (pcmS16LE160Samples.length != 320) {
            throw new IllegalArgumentException(
                    "expected 320 bytes (160 samples at 16-bit), got " + pcmS16LE160Samples.length);
        }
        requireTarget();
        byte[] ulaw = MuLawCodec.encodeBuffer(pcmS16LE160Samples, 0, pcmS16LE160Samples.length);
        int seq = audioSequenceNumber.getAndIncrement();
        long timestamp = seq * 160L; // 160 samples/frame at an 8kHz RTP clock
        send(RtpPacket.build(0 /* PCMU */, false, seq, timestamp, ssrc, ulaw));
    }

    /**
     * Send a complete RFC 4733 DTMF event to synauson's local RTP port: a
     * marker-bit-set first packet, duration-increment packets at the
     * standard 20ms packetization interval, and a final end-marked packet —
     * matching how a real DTMF sender behaves.
     *
     * @param eventNumber      RFC 4733 event number, 0-15
     * @param durationMs       total event duration in milliseconds
     * @param dtmfPayloadType  the payload type configured for DTMF on the target participant
     */
    public void sendDtmfEvent(int eventNumber, int durationMs, int dtmfPayloadType) throws IOException {
        requireTarget();
        int volume = 10; // -10 dBm0, matches synauson-core's own send-side default (participants/sip/dtmf.rs)
        int totalDurationSamples = durationMs * 8; // 8kHz clock
        int step = 160; // one 20ms packetization interval at 8kHz

        int seq = dtmfSequenceNumber.getAndIncrement();
        long eventTimestamp = seq * 160L; // shared across the whole event per RFC 4733 section 2.5.1.3
        boolean first = true;
        for (int elapsed = step; elapsed < totalDurationSamples; elapsed += step) {
            byte[] payload = buildDtmfPayload(eventNumber, false, volume, elapsed);
            send(RtpPacket.build(dtmfPayloadType, first, seq, eventTimestamp, ssrc, payload));
            first = false;
            seq = dtmfSequenceNumber.getAndIncrement();
        }
        byte[] endPayload = buildDtmfPayload(eventNumber, true, volume, totalDurationSamples);
        send(RtpPacket.build(dtmfPayloadType, first, seq, eventTimestamp, ssrc, endPayload));
    }

    private static byte[] buildDtmfPayload(int eventNumber, boolean endOfEvent, int volume, int durationSamples) {
        byte[] payload = new byte[4];
        payload[0] = (byte) eventNumber;
        payload[1] = (byte) ((endOfEvent ? 0x80 : 0x00) | (volume & 0x3F));
        payload[2] = (byte) ((durationSamples >> 8) & 0xFF);
        payload[3] = (byte) (durationSamples & 0xFF);
        return payload;
    }

    private void requireTarget() {
        if (targetAddress == null || targetPort < 0) {
            throw new IllegalStateException("setTarget(host, port) must be called before sending");
        }
    }

    private void send(byte[] wire) throws IOException {
        socket.send(new DatagramPacket(wire, wire.length, targetAddress, targetPort));
    }

    /** Snapshot of every RTP packet received at this peer's bound port so far. */
    public List<RtpPacket> capturedPackets() {
        return new ArrayList<>(captured);
    }

    @Override
    public void close() {
        running.set(false);
        try {
            receiveThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        socket.close();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.support.SipRtpPeerTest' --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Verify DTMF packet shape against the canonical GStreamer element**

Per this repo's CLAUDE.md hard rule ("research GStreamer before writing it"), synauson-core's receive side depayloads inbound DTMF with the real `rtpdtmfdepay` element (`synauson-core/src/participants/sip/pipeline.rs:532`, `synauson-core/src/participants/sip/pipeline.rs:524` — `attach_dtmf_depay_chain`), not custom Rust parsing. Before trusting the packet shape above, clone **both** `gst-plugins-good` (the element itself) **and** core `gstreamer` (the `GstRTPBaseDepayload`/`GstRTPBasePayload` classes `rtpdtmfdepay` is built on, plus `rtpbin`'s jitterbuffer/payload-type-demux behavior, which decides how packets reach the depayloader in the first place) into `/tmp`, and actually read the code — do not rely on memory of RFC 4733 alone:

```bash
git clone --depth 1 https://gitlab.freedesktop.org/gstreamer/gstreamer.git /tmp/gstreamer
git clone --depth 1 https://gitlab.freedesktop.org/gstreamer/gst-plugins-good.git /tmp/gst-plugins-good

# The element itself: how it reads the 4-byte RFC 4733 payload, what it does
# with the marker bit and the end-of-event bit, whether it requires multiple
# packets per event or accepts a single one.
grep -n "marker\|duration\|E_BIT\|0x80\|GST_RTP_BUFFER" \
    /tmp/gst-plugins-good/subprojects/gst-plugins-good/gst/rtp/gstrtpdtmfdepay.c \
    /tmp/gst-plugins-good/gst/rtp/gstrtpdtmfdepay.c 2>/dev/null

# The base class it extends: how GstRTPBaseDepayload hands buffers to
# subclasses (timestamp/seqnum handling, whether it re-orders or drops
# anything relevant to a multi-packet DTMF event).
find /tmp/gstreamer -iname "gstrtpbasedepayload.c"
```

Note any discrepancy found; if the depayloader expects something different from the multi-packet marker/duration/end-bit sequence `SipRtpPeer.sendDtmfEvent` produces, fix it now rather than discovering it via a failing IT test in Task 5.

- [ ] **Step 6: Commit**

```bash
cd jsyn
git add src/test/java/com/synauson/jsyn/it/support/SipRtpPeer.java \
        src/test/java/com/synauson/jsyn/it/support/SipRtpPeerTest.java
git commit -S -m "add SipRtpPeer real RTP/RFC4733 test peer

Why: closes the exact gap SipParticipantIT (\"does not require real RTP
traffic\") and DtmfEventsIT (\"an event-arrival test requires a real RFC
4733 RTP loopback sender (out of scope)\") both flag. SipRtpPeer is that
loopback sender — real PCMU audio, real multi-packet RFC 4733 DTMF events,
verified against gst-plugins-good's rtpdtmfdepay.c so the packet shape
matches synauson-core's actual receive-side depayloader.

**SipRtpPeer**
Construction is split from setTarget(...) because the peer's own local
port must be known before addSipParticipant (as remoteRtpPort), while
synauson's local port — the peer's send target — is only known from the
handle addSipParticipant returns."
```

---

### Task 4: `SipMediaE2eIT` — real audio in and out over SIP

**Files:**
- Create: `jsyn/src/test/java/com/synauson/jsyn/it/SipMediaE2eIT.java`

This is the first task that exercises the real `synauson` JNI boundary, so it cannot be run standalone the way Tasks 1–3 could — it needs the sibling repo checked out and the native library on the classpath, same as every other `*IT.java` test. If you don't have `~/projects/synauson/synauson` checked out locally, this test's `Assumptions.assumeTrue` will make it skip rather than fail; that's expected for local dev, not for CI (see Task 9).

- [ ] **Step 1: Write the test**

```java
package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.VadEvent;
import com.synauson.jsyn.it.support.RtpPacket;
import com.synauson.jsyn.it.support.SipRtpPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.SipParticipantSpec;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep SIP media test using {@link SipRtpPeer}: real inbound RTP/PCMU audio
 * triggers VAD on the SIP path, and real outbound RTP/PCMU audio is
 * captured actually leaving the wire.
 *
 * <p>Unlike {@link SipParticipantIT} (port allocation and handle lifecycle
 * only — "does not require real RTP traffic" per its own docstring), this
 * pushes actual RTP packets over a real UDP socket in both directions.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SipMediaE2eIT {

    @Test
    void inboundAudioTriggersVadOverSipPath() throws Exception {
        Path speechWav = JSynTestHelpers.resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures/short_speech.wav");
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav not found — skipping SIP media IT");

        byte[] pcm16k = JSynTestHelpers.readPcmFromWav(speechWav);
        byte[] pcm8k = downsampleS16LE16kTo8k(pcm16k);

        long ts = System.nanoTime();
        String confId = "sip-media-it-" + ts;
        String pid = "sip-media-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(pid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(101)
                            .vad(VadConfig.defaults())
                            .build());
            assertNotNull(handle);
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            CountDownLatch speechStart = new CountDownLatch(1);
            try (Subscription sub = conf.streamVadEvents(pid, ev -> {
                if (ev instanceof VadEvent.SpeechStart) {
                    speechStart.countDown();
                }
            })) {
                int frameBytes = 320;
                for (int repeat = 0; repeat < 3 && speechStart.getCount() > 0; repeat++) {
                    int offset = 0;
                    while (offset + frameBytes <= pcm8k.length && speechStart.getCount() > 0) {
                        byte[] frame = new byte[frameBytes];
                        System.arraycopy(pcm8k, offset, frame, 0, frameBytes);
                        peer.sendAudioFrame(frame);
                        offset += frameBytes;
                        Thread.sleep(18);
                    }
                }
                assertTrue(speechStart.await(10, TimeUnit.SECONDS),
                        "SpeechStart did not arrive from real inbound SIP RTP audio");
            }

            conf.removeParticipant(pid);
        }
    }

    @Test
    void outboundAudioLeavesTheWire() throws Exception {
        long ts = System.nanoTime();
        String confId = "sip-out-it-" + ts;
        String sipPid = "sip-out-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(sipPid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(101)
                            .build());
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            // Self-route so the SIP participant's tee has a downstream sink and
            // its own inbound audio gets mixed straight back to it.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(sipPid, sipPid)));

            byte[] tone = generateSinePcm8k(440.0, 2.0);
            int frameBytes = 320;
            long deadline = System.currentTimeMillis() + 2000;
            int offset = 0;
            while (System.currentTimeMillis() < deadline) {
                byte[] frame = new byte[frameBytes];
                for (int i = 0; i < frameBytes; i++) {
                    frame[i] = tone[(offset + i) % tone.length];
                }
                peer.sendAudioFrame(frame);
                offset += frameBytes;
                Thread.sleep(18);
            }

            List<RtpPacket> captured = peer.capturedPackets();
            assertFalse(captured.isEmpty(),
                    "expected synauson to send real RTP audio back to the peer's socket");
            assertTrue(captured.stream().anyMatch(p -> p.payloadType == 0),
                    "expected at least one captured packet with PCMU payload type 0");

            conf.removeParticipant(sipPid);
        }
    }

    private static byte[] downsampleS16LE16kTo8k(byte[] pcm16k) {
        int frames16k = pcm16k.length / 2;
        int frames8k = frames16k / 2;
        byte[] out = new byte[frames8k * 2];
        for (int i = 0; i < frames8k; i++) {
            out[i * 2] = pcm16k[i * 4];
            out[i * 2 + 1] = pcm16k[i * 4 + 1];
        }
        return out;
    }

    private static byte[] generateSinePcm8k(double freqHz, double durationSeconds) {
        int sampleRate = 8_000;
        int totalSamples = (int) (sampleRate * durationSeconds);
        byte[] buf = new byte[totalSamples * 2];
        for (int n = 0; n < totalSamples; n++) {
            double t = n / (double) sampleRate;
            short s = (short) (16_000.0 * Math.sin(2.0 * Math.PI * freqHz * t));
            buf[n * 2] = (byte) (s & 0xff);
            buf[n * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return buf;
    }
}
```

- [ ] **Step 2: Run and confirm real execution (not a skip)**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.SipMediaE2eIT' --no-daemon --console=plain -DsynausonRepoDir=$HOME/projects/synauson/synauson`
Expected first attempt: may fail — this is genuinely new integration surface (first time real RTP audio has ever been pushed into a SIP participant from a test). If `inboundAudioTriggersVadOverSipPath` times out waiting for `SpeechStart`, check: (a) is `remoteRtpPort`/`localRtpPort` wiring correct — add a debug `System.out.println` of both ports and confirm `SipRtpPeer.setTarget` uses the right one; (b) is the 8kHz downsample producing audible speech — dump `pcm8k` to a WAV via `JSynTestHelpers`-style header writing and listen to it if needed. Iterate until both tests pass for a real (non-assumption-skipped) run.

- [ ] **Step 3: Commit**

```bash
cd jsyn
git add src/test/java/com/synauson/jsyn/it/SipMediaE2eIT.java
git commit -S -m "add SipMediaE2eIT: real inbound/outbound audio over SIP

Why: SipParticipantIT only proves port allocation and handle lifecycle.
This proves real RTP/PCMU audio actually triggers VAD when it arrives, and
that synauson's outbound audio genuinely leaves the wire — the same depth
already proven for Native/Recording/File participants, now for SIP."
```

---

### Task 5: `SipDtmfE2eIT` — real bidirectional DTMF

**Files:**
- Create: `jsyn/src/test/java/com/synauson/jsyn/it/SipDtmfE2eIT.java`

- [ ] **Step 1: Write the test**

```java
package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.DtmfEvent;
import com.synauson.jsyn.it.support.RtpPacket;
import com.synauson.jsyn.it.support.SipRtpPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.SipParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep bidirectional DTMF test using real RFC 4733 RTP packets via
 * {@link SipRtpPeer}.
 *
 * <p>Closes the gap {@link DtmfEventsIT}'s own docstring flags as out of
 * scope: "an event-arrival test requires a real RFC 4733 RTP loopback
 * sender." {@link SipRtpPeer} is that loopback sender.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SipDtmfE2eIT {

    private static final int DTMF_PAYLOAD_TYPE = 101;

    @Test
    void sendDtmfProducesRealRfc4733WireFrame() throws Exception {
        long ts = System.nanoTime();
        String confId = "dtmf-send-it-" + ts;
        String pid = "dtmf-send-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(pid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(DTMF_PAYLOAD_TYPE)
                            .build());
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            handle.sendDtmf('5', 150);

            long deadline = System.currentTimeMillis() + 5000;
            boolean found = false;
            while (System.currentTimeMillis() < deadline && !found) {
                for (RtpPacket p : peer.capturedPackets()) {
                    if (p.payloadType == DTMF_PAYLOAD_TYPE && p.payload.length >= 1
                            && (p.payload[0] & 0xFF) == 5) {
                        found = true;
                        break;
                    }
                }
                if (!found) Thread.sleep(50);
            }
            assertTrue(found, "expected a real RFC 4733 wire packet for digit '5' (event 5)");

            conf.removeParticipant(pid);
        }
    }

    @Test
    void remoteDtmfEventFiresOverRfc4733() throws Exception {
        long ts = System.nanoTime();
        String confId = "dtmf-recv-it-" + ts;
        String pid = "dtmf-recv-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(pid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(DTMF_PAYLOAD_TYPE)
                            .build());
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            CountDownLatch gotEvent = new CountDownLatch(1);
            AtomicReference<DtmfEvent> received = new AtomicReference<>();
            try (Subscription sub = conf.streamDtmfEvents(pid, ev -> {
                received.set(ev);
                gotEvent.countDown();
            })) {
                peer.sendDtmfEvent(9, 150, DTMF_PAYLOAD_TYPE);

                assertTrue(gotEvent.await(10, TimeUnit.SECONDS),
                        "expected a DtmfEvent from a real inbound RFC 4733 sender");
                assertEquals("9", received.get().digit);
                assertTrue(received.get().inBand, "RFC 4733 events are in-band by definition");
            }

            conf.removeParticipant(pid);
        }
    }
}
```

- [ ] **Step 2: Run and iterate**

Run: `cd jsyn && ./gradlew :jsyn:test --tests 'com.synauson.jsyn.it.SipDtmfE2eIT' --no-daemon --console=plain -DsynausonRepoDir=$HOME/projects/synauson/synauson`
Expected: this is the highest-risk test in the plan for a first-attempt mismatch, since it depends on `rtpdtmfdepay`'s exact expected packet shape. If `remoteDtmfEventFiresOverRfc4733` fails, re-check Task 3 Step 5's `rtpdtmfdepay.c` findings and adjust `SipRtpPeer.sendDtmfEvent` — do not guess a second time; re-read the source. If `sendDtmfProducesRealRfc4733WireFrame` fails, verify the digit-to-event-number mapping matches `synauson-core/src/participants/sip/dtmf.rs::digit_to_event_number` (digit `'5'` → event `5` is already confirmed by that file's own test).

- [ ] **Step 3: Commit**

```bash
cd jsyn
git add src/test/java/com/synauson/jsyn/it/SipDtmfE2eIT.java
git commit -S -m "add SipDtmfE2eIT: real bidirectional RFC 4733 DTMF

Why: DtmfEventsIT only proves sendDtmf()'s API contract (valid/invalid
chars, no throw) — its own docstring says event-arrival testing needs a
real RTP loopback sender and is out of scope. SipRtpPeer is that sender;
this proves both that sendDtmf() produces a correct wire frame and that a
genuine remote DTMF send fires a real DtmfEvent."
```

---

### Task 6: Playwright-Java dependency + browser install task

**Files:**
- Modify: `jsyn/build.gradle.kts`

- [ ] **Step 1: Add the dependency and the install task**

Add to the existing `dependencies { }` block:

```kotlin
    testImplementation("com.microsoft.playwright:playwright:1.47.0")
```

Add a new top-level task (alongside the existing `tasks.test { }` block):

```kotlin
tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "verification"
    description = "Downloads the Chromium build Playwright drives for WebRtcMediaE2eIT. " +
        "Set INSTALL_PLAYWRIGHT_DEPS=1 to also install Linux OS-level dependencies (CI only)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args = if (System.getenv("INSTALL_PLAYWRIGHT_DEPS") == "1") {
        listOf("install", "chromium", "--with-deps")
    } else {
        listOf("install", "chromium")
    }
}
```

- [ ] **Step 2: Run it locally to confirm it downloads Chromium**

Run: `cd jsyn && ./gradlew :jsyn:installPlaywrightBrowsers --no-daemon --console=plain`
Expected: Gradle resolves `com.microsoft.playwright:playwright:1.47.0` and its transitive deps, then Playwright's CLI downloads a Chromium build (progress output, ends without error). Confirm the browser landed: `ls ~/.cache/ms-playwright/` (Linux) should show a `chromium-*` directory.

- [ ] **Step 3: Commit**

```bash
cd jsyn
git add build.gradle.kts
git commit -S -m "add Playwright-Java dependency and browser install task

Why: WebRtcBrowserPeer (next task) needs a real headless Chromium instance
to act as the WebRTC counterparty for real ICE/DTLS-SRTP/Opus testing.
Playwright-Java is a normal Gradle test dependency that bundles its own
Chromium — no Node.js or changes to the synauson repo required.

INSTALL_PLAYWRIGHT_DEPS=1 gates --with-deps (apt-based OS dependency
install) so the task stays usable on a developer's machine without sudo;
CI sets it explicitly on Linux only (Windows doesn't need or support it)."
```

---

### Task 7: `WebRtcBrowserPeer`

**Files:**
- Create: `jsyn/src/test/java/com/synauson/jsyn/it/support/WebRtcBrowserPeer.java`

No unit test for this one — every method requires a real Chromium instance, so it's exercised end-to-end by `WebRtcMediaE2eIT` (Task 8) rather than in isolation. This matches how `SipRtpPeer` could be tested standalone (Task 3) but `WebRtcMediaE2eIT` cannot: there's no equivalent "loop it back to itself" trick for a browser peer that doesn't already require the exact glue code under test.

- [ ] **Step 1: Write the implementation**

```java
package com.synauson.jsyn.it.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A real headless Chromium browser acting as the WebRTC counterparty for
 * {@code WebRtcMediaE2eIT}: real ICE, real DTLS-SRTP, real Opus.
 *
 * <p>Serves a tiny static page over {@code http://127.0.0.1} — a secure
 * context per the W3C spec's loopback exception, so {@code getUserMedia}
 * works without HTTPS — and drives it via Playwright's {@link Page#evaluate}.
 * Chromium is launched with {@code --use-fake-device-for-media-stream} and
 * {@code --use-fake-ui-for-media-stream} so no real camera/microphone
 * hardware or permission UI is needed; this is the standard, widely-used
 * approach for headless-CI WebRTC testing.
 */
public final class WebRtcBrowserPeer implements AutoCloseable {

    private static final String PAGE_HTML = String.join("\n",
            "<!DOCTYPE html>",
            "<html><body>",
            "<script>",
            "window.__pc = null;",
            "window.__iceQueue = [];",
            "window.__maxAmplitude = 0;",
            "",
            "async function createOffer() {",
            "  window.__pc = new RTCPeerConnection({iceServers: [{urls: 'stun:stun.l.google.com:19302'}]});",
            "  window.__pc.onicecandidate = function(event) {",
            "    if (event.candidate) {",
            "      window.__iceQueue.push({",
            "        candidate: event.candidate.candidate,",
            "        sdpMLineIndex: event.candidate.sdpMLineIndex",
            "      });",
            "    }",
            "  };",
            "  window.__pc.ontrack = function(event) {",
            "    var audioCtx = new AudioContext();",
            "    var source = audioCtx.createMediaStreamSource(event.streams[0]);",
            "    var analyser = audioCtx.createAnalyser();",
            "    analyser.fftSize = 2048;",
            "    source.connect(analyser);",
            "    var data = new Uint8Array(analyser.fftSize);",
            "    setInterval(function() {",
            "      analyser.getByteTimeDomainData(data);",
            "      var maxDev = 0;",
            "      for (var i = 0; i < data.length; i++) {",
            "        var dev = Math.abs(data[i] - 128);",
            "        if (dev > maxDev) maxDev = dev;",
            "      }",
            "      if (maxDev > window.__maxAmplitude) window.__maxAmplitude = maxDev;",
            "    }, 20);",
            "  };",
            "  var stream = await navigator.mediaDevices.getUserMedia({audio: true});",
            "  stream.getTracks().forEach(function(t) { window.__pc.addTrack(t, stream); });",
            "  var offer = await window.__pc.createOffer();",
            "  await window.__pc.setLocalDescription(offer);",
            "  return offer.sdp;",
            "}",
            "",
            "async function applyAnswer(sdp) {",
            "  await window.__pc.setRemoteDescription({type: 'answer', sdp: sdp});",
            "}",
            "",
            "async function addRemoteIceCandidate(candidate, sdpMLineIndex) {",
            "  await window.__pc.addIceCandidate({candidate: candidate, sdpMLineIndex: sdpMLineIndex});",
            "}",
            "",
            "function drainIceCandidates() {",
            "  var q = window.__iceQueue;",
            "  window.__iceQueue = [];",
            "  return q;",
            "}",
            "",
            "function maxObservedAmplitude() {",
            "  return window.__maxAmplitude;",
            "}",
            "</script>",
            "</body></html>"
    );

    private final HttpServer httpServer;
    private final Playwright playwright;
    private final Browser browser;
    private final Page page;

    /** Launch with Chromium's default synthetic fake-microphone tone. */
    public WebRtcBrowserPeer() throws IOException {
        this(null);
    }

    /**
     * Launch with a real audio file (e.g. a speech WAV) as the fake
     * microphone's input, via Chromium's {@code --use-file-for-fake-audio-capture}.
     *
     * @param fakeAudioCaptureFile path to a WAV file, or {@code null} for the default tone
     */
    public WebRtcBrowserPeer(String fakeAudioCaptureFile) throws IOException {
        this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            byte[] body = PAGE_HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.start();

        List<String> args = new ArrayList<>(List.of(
                "--use-fake-device-for-media-stream",
                "--use-fake-ui-for-media-stream"));
        if (fakeAudioCaptureFile != null) {
            args.add("--use-file-for-fake-audio-capture=" + fakeAudioCaptureFile);
        }

        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setArgs(args));
        this.page = browser.newPage();
        page.navigate("http://127.0.0.1:" + httpServer.getAddress().getPort() + "/");
    }

    /** Drive the browser to create a real SDP offer with a real ICE ufrag/DTLS fingerprint. */
    public String createOffer() {
        return (String) page.evaluate("() => createOffer()");
    }

    /** Apply synauson's real SDP answer to the browser's peer connection. */
    public void applyAnswer(String sdpAnswer) {
        page.evaluate("(sdp) => applyAnswer(sdp)", sdpAnswer);
    }

    /** Push a trickle-ICE candidate from synauson into the browser's peer connection. */
    public void addRemoteIceCandidate(String candidate, int sdpMLineIndex) {
        page.evaluate("(args) => addRemoteIceCandidate(args.candidate, args.sdpMLineIndex)",
                Map.of("candidate", candidate, "sdpMLineIndex", sdpMLineIndex));
    }

    /** Every local ICE candidate the browser has generated since the last drain. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> drainLocalIceCandidates() {
        Object result = page.evaluate("() => drainIceCandidates()");
        return result == null ? new ArrayList<>() : (List<Map<String, Object>>) result;
    }

    /**
     * Polls the browser's incoming-audio amplitude meter until it exceeds a
     * non-silence threshold or {@code within} elapses.
     */
    public boolean receivedNonSilentAudio(Duration within) {
        long deadline = System.currentTimeMillis() + within.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Object result = page.evaluate("() => maxObservedAmplitude()");
            double amplitude = ((Number) result).doubleValue();
            if (amplitude > 10.0) { // out of a possible 0-128 deviation range
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public void close() {
        page.close();
        browser.close();
        playwright.close();
        httpServer.stop(0);
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `cd jsyn && ./gradlew :jsyn:compileTestJava --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`. This only proves it compiles — real behavior is proven by Task 8's IT test.

- [ ] **Step 3: Commit**

```bash
cd jsyn
git add src/test/java/com/synauson/jsyn/it/support/WebRtcBrowserPeer.java
git commit -S -m "add WebRtcBrowserPeer real headless-Chromium test peer

Why: WebRtcParticipantIT only exercises SDP signaling with a canned offer
(\"a full ICE + media exchange requires a second GStreamer peer; this test
only exercises the SDP signaling half\", per its own docstring). A real
browser is the most authoritative possible WebRTC peer — real ICE, real
DTLS-SRTP, real Opus — and needs no changes to the synauson repo since
jsyn already has full trickle-ICE support.

**WebRtcBrowserPeer**
Serves its JS harness over http://127.0.0.1 (a secure context per the W3C
loopback exception) via the JDK's built-in HttpServer, so getUserMedia
works without HTTPS. Chromium's fake-device/fake-ui flags avoid needing
real hardware or a permission prompt."
```

---

### Task 8: `WebRtcMediaE2eIT` — real speech through WebRTC

**Files:**
- Create: `jsyn/src/test/java/com/synauson/jsyn/it/WebRtcMediaE2eIT.java`

- [ ] **Step 1: Write the test**

```java
package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.VadEvent;
import com.synauson.jsyn.it.support.WebRtcBrowserPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.WebRtcParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.VadConfig;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep WebRTC media test: a real headless Chromium browser negotiates real
 * ICE/DTLS-SRTP/Opus with synauson and plays real speech as its fake
 * microphone, proving VAD fires through the actual WebRTC transport —
 * something {@link WebRtcParticipantIT}'s canned-SDP signaling-only test
 * never exercises.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class WebRtcMediaE2eIT {

    @Test
    void realSpeechThroughWebRtcTriggersVad() throws Exception {
        Path speechWav = JSynTestHelpers.resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures/short_speech.wav");
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav not found — skipping WebRTC media IT");

        long ts = System.nanoTime();
        String confId = "webrtc-media-it-" + ts;
        String pid = "webrtc-media-p-" + ts;

        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer(speechWav.toString());
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            String offer = browser.createOffer();
            assertTrue(offer.startsWith("v=0"), "browser must produce a real SDP offer");

            WebRtcParticipantHandle handle = conf.addWebRtcParticipant(
                    WebRtcParticipantSpec.builder()
                            .participantId(pid)
                            .sdpOffer(offer)
                            .stunServer("stun://stun.l.google.com:19302")
                            .jitterBufferMs(200)
                            .vad(VadConfig.defaults())
                            .build());
            browser.applyAnswer(handle.sdpAnswer());

            CountDownLatch speechStart = new CountDownLatch(1);
            try (Subscription vadSub = conf.streamVadEvents(pid, ev -> {
                if (ev instanceof VadEvent.SpeechStart) {
                    speechStart.countDown();
                }
            });
                 Subscription iceSub = conf.streamWebRtcIceCandidates(pid, ev -> {
                     if (!ev.endOfCandidates) {
                         browser.addRemoteIceCandidate(ev.candidate, ev.sdpMLineIndex);
                     }
                 })) {

                // Relay the browser's own trickle-ICE candidates into synauson,
                // polling because the browser generates them asynchronously.
                long iceDeadline = System.currentTimeMillis() + 30_000;
                while (System.currentTimeMillis() < iceDeadline && speechStart.getCount() > 0) {
                    for (Map<String, Object> candidate : browser.drainLocalIceCandidates()) {
                        handle.addIceCandidate(
                                (String) candidate.get("candidate"),
                                ((Number) candidate.get("sdpMLineIndex")).intValue());
                    }
                    if (speechStart.await(500, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }

                assertTrue(speechStart.getCount() == 0,
                        "SpeechStart did not arrive through the real WebRTC media path");
            }

            conf.removeParticipant(pid);
        }
    }

    @Test
    void audioFlowsBackToTheBrowser() throws Exception {
        long ts = System.nanoTime();
        String confId = "webrtc-echo-it-" + ts;
        String pid = "webrtc-echo-p-" + ts;

        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer(); // default synthetic fake-mic tone
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            String offer = browser.createOffer();
            WebRtcParticipantHandle handle = conf.addWebRtcParticipant(
                    WebRtcParticipantSpec.builder()
                            .participantId(pid)
                            .sdpOffer(offer)
                            .stunServer("stun://stun.l.google.com:19302")
                            .jitterBufferMs(200)
                            .build());
            browser.applyAnswer(handle.sdpAnswer());

            // Self-route so synauson sends the browser's own audio straight back.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));

            try (Subscription iceSub = conf.streamWebRtcIceCandidates(pid, ev -> {
                if (!ev.endOfCandidates) {
                    browser.addRemoteIceCandidate(ev.candidate, ev.sdpMLineIndex);
                }
            })) {
                long iceDeadline = System.currentTimeMillis() + 10_000;
                while (System.currentTimeMillis() < iceDeadline) {
                    for (Map<String, Object> candidate : browser.drainLocalIceCandidates()) {
                        handle.addIceCandidate(
                                (String) candidate.get("candidate"),
                                ((Number) candidate.get("sdpMLineIndex")).intValue());
                    }
                    Thread.sleep(500);
                }

                assertTrue(browser.receivedNonSilentAudio(Duration.ofSeconds(15)),
                        "browser did not observe non-silent audio echoed back over WebRTC");
            }

            conf.removeParticipant(pid);
        }
    }
}
```

- [ ] **Step 2: Research webrtcbin's negotiation internals before debugging blind**

Per CLAUDE.md's GStreamer hard rule, `synauson-core`'s WebRTC participant is built on `webrtcbin` (`gst-plugins-bad`), which itself drives `rtpbin`/ICE/DTLS-SRTP internally (core `gstreamer` + `gst-plugins-bad`'s `nice`/`dtls` elements). Before iterating on failures in Step 3 by trial and error, clone both and read the actual negotiation flow so any failure can be diagnosed against real signal/property behavior instead of guesswork:

```bash
git clone --depth 1 https://gitlab.freedesktop.org/gstreamer/gstreamer.git /tmp/gstreamer   # if not already cloned in Task 3
git clone --depth 1 https://gitlab.freedesktop.org/gstreamer/gst-plugins-bad.git /tmp/gst-plugins-bad

# webrtcbin's own negotiation state machine: on-negotiation-needed, ICE
# gathering/connection-state signals, add-ice-candidate, and how it reports
# a completed DTLS-SRTP handshake.
find /tmp/gst-plugins-bad -iname "gstwebrtcbin.c"
grep -n "on-negotiation-needed\|on-ice-candidate\|add-ice-candidate\|ice-connection-state\|dtls" \
    /tmp/gst-plugins-bad/subprojects/gst-plugins-bad/ext/webrtc/gstwebrtcbin.c \
    /tmp/gst-plugins-bad/ext/webrtc/gstwebrtcbin.c 2>/dev/null | head -60
```

Cross-check this against `synauson-core`'s own webrtcbin usage (`synauson-core/src/participants/webrtc/`) and the FakeAgent test scaffolding referenced in the design doc (`synauson-server/tests/support/fake_agent/signal_handlers.rs`, `.../ice.rs`) — that code already wires these exact signals correctly for a second-webrtcbin-in-the-same-process peer, so it's a working reference for the same negotiation sequence `WebRtcBrowserPeer`'s JS side must also follow (offer → local description → ICE candidates → remote description → remote ICE candidates → connected).

- [ ] **Step 3: Run and iterate**

Run: `cd jsyn && ./gradlew :jsyn:installPlaywrightBrowsers :jsyn:test --tests 'com.synauson.jsyn.it.WebRtcMediaE2eIT' --no-daemon --console=plain -DsynausonRepoDir=$HOME/projects/synauson/synauson`

This is the highest-uncertainty test in the whole plan — it is the first time this codebase has ever driven a real browser against synauson's WebRTC path. Expect to iterate, now informed by Step 2's research rather than guessing. Specific things to check if it fails:
- If `createOffer()` throws or returns empty: check the Playwright/Chromium launch actually succeeded (`page.evaluate` swallows JS exceptions into a Java `PlaywrightException` — read its message, it includes the JS stack trace).
- If ICE never completes (no `SpeechStart`, no non-silent audio, and no explicit error): add temporary logging of `conf.streamWebRtcIceCandidates` and `browser.drainLocalIceCandidates()` sizes on each poll iteration to confirm candidates are flowing both directions, and compare the observed `ice-connection-state` transitions against what Step 2 found in `gstwebrtcbin.c` for a healthy handshake.
- If `--use-file-for-fake-audio-capture` with the WAV path doesn't produce real speech (e.g. `receivedNonSilentAudio` never trips in the *first* test but works in the second synthetic-tone test): this narrows the problem to the fake-audio-capture flag's expected file format specifically, not the ICE/DTLS/SRTP path. Try re-encoding `short_speech.wav` to 48kHz mono 16-bit (Chromium's audio pipeline's native capture rate) via `ffmpeg -i short_speech.wav -ar 48000 -ac 1 short_speech_48k.wav` and pointing the flag at that file instead.

- [ ] **Step 4: Commit**

```bash
cd jsyn
git add src/test/java/com/synauson/jsyn/it/WebRtcMediaE2eIT.java
git commit -S -m "add WebRtcMediaE2eIT: real speech through the WebRTC path

Why: WebRtcParticipantIT never proves audio flows — only that SDP
signaling completes with a canned offer. This drives a real headless
Chromium browser through real ICE/DTLS-SRTP/Opus negotiation and real
speech playback, proving VAD fires through the actual WebRTC transport in
both directions (browser->synauson and synauson->browser)."
```

---

### Task 9: Wire CI — Playwright Chromium install + cache on both platforms

**Files:**
- Modify: `jsyn/.github/workflows/ci.yml`

- [ ] **Step 1: Add to `test-linux`**

Add a job-level `env:` key (as a sibling of `runs-on`/`needs`/`timeout-minutes`), and two new steps after the existing "Install ONNX Runtime 1.24.4" step and before "Set up Temurin 11" (order doesn't matter functionally, but this keeps all environment-setup steps grouped before the Java/Gradle steps, matching the job's existing structure):

```yaml
  test-linux:
    runs-on: ubuntu-24.04
    needs: java-compile
    timeout-minutes: 30
    env:
      PLAYWRIGHT_BROWSERS_PATH: ${{ github.workspace }}/.playwright-browsers
    steps:
      - uses: actions/checkout@v4
      # ... existing steps unchanged up through "Install ONNX Runtime 1.24.4" ...

      - name: Cache Playwright Chromium
        uses: actions/cache@v4
        with:
          path: ${{ env.PLAYWRIGHT_BROWSERS_PATH }}
          key: ${{ runner.os }}-playwright-chromium-1.47.0

      # ... existing "Set up Temurin 11" and "Cache Gradle wrapper + caches" steps unchanged ...

      - name: Install Playwright Chromium
        env:
          INSTALL_PLAYWRIGHT_DEPS: "1"
        run: ./gradlew :jsyn:installPlaywrightBrowsers --no-daemon --console=plain

      - name: gradle :jsyn:test
        env:
          NEXUS_USER: ${{ secrets.NEXUS_USER }}
          NEXUS_PASSWORD: ${{ secrets.NEXUS_PASSWORD }}
        run: |
          ./gradlew :jsyn:test --no-daemon --console=plain \
            -DsynausonRepoDir="${{ github.workspace }}/synauson-repo"
```

- [ ] **Step 2: Add to `test-windows`**

Same shape, no `INSTALL_PLAYWRIGHT_DEPS` (Playwright's `--with-deps` is Linux/macOS-only and unnecessary on Windows):

```yaml
  test-windows:
    runs-on: windows-2022
    needs: java-compile
    timeout-minutes: 45
    env:
      PLAYWRIGHT_BROWSERS_PATH: ${{ github.workspace }}/.playwright-browsers
    steps:
      - uses: actions/checkout@v4
      # ... existing steps unchanged through "Extract ONNX Runtime" ...

      - name: Cache Playwright Chromium
        uses: actions/cache@v4
        with:
          path: ${{ env.PLAYWRIGHT_BROWSERS_PATH }}
          key: ${{ runner.os }}-playwright-chromium-1.47.0

      # ... existing "Set up Temurin 11" and "Cache Gradle wrapper + caches" steps unchanged ...

      - name: Install Playwright Chromium
        shell: pwsh
        run: ./gradlew.bat :jsyn:installPlaywrightBrowsers --no-daemon --console=plain

      - name: gradle :jsyn:test
        env:
          NEXUS_USER: ${{ secrets.NEXUS_USER }}
          NEXUS_PASSWORD: ${{ secrets.NEXUS_PASSWORD }}
        shell: pwsh
        run: |
          ./gradlew.bat :jsyn:test --no-daemon --console=plain `
            "-DsynausonRepoDir=${{ github.workspace }}\synauson-repo"
```

- [ ] **Step 3: Validate YAML locally**

Run: `cd jsyn && python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo "valid YAML"`
Expected: `valid YAML` with no exceptions.

- [ ] **Step 4: Commit**

```bash
cd jsyn
git add .github/workflows/ci.yml
git commit -S -m "wire Playwright Chromium install into test-linux/test-windows

Why: WebRtcMediaE2eIT (added in Task 8) needs a real Chromium browser
available in CI. Folds into the existing two test jobs rather than adding
a new job — same pattern as the GStreamer/ORT setup already in each job.

Side effects: INSTALL_PLAYWRIGHT_DEPS=1 is set on Linux only (apt-based
--with-deps is Linux/macOS-only in Playwright); Chromium is cached via
actions/cache keyed on the pinned Playwright version, same caching
approach already used for GStreamer/ORT downloads in these jobs."
```

---

### Task 10: Push and iterate until green

**Files:** none (verification only)

- [ ] **Step 1: Faithful local reproduction for the Linux side**

Following the same approach used for the prior CI integration testing effort (`docs/superpowers/plans/2026-08-05-ci-integration-testing.md` Task 5), reproduce the exact `ubuntu-24.04` CI environment in a container before spending real CI minutes:

```bash
podman run --rm -it -v ~/projects/synauson:/repos:ro ubuntu:24.04 bash
# Inside the container:
apt-get update && apt-get install -y --no-install-recommends \
    libgstreamer1.0-0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav \
    gstreamer1.0-nice curl unzip openjdk-11-jdk-headless
# ... extract ORT 1.24.4 the same way ci.yml does ...
cp -r /repos/jsyn /tmp/jsyn && cp -r /repos/synauson /tmp/synauson-repo
cd /tmp/jsyn
./gradlew :jsyn:installPlaywrightBrowsers --no-daemon --console=plain
INSTALL_PLAYWRIGHT_DEPS=1 ./gradlew :jsyn:installPlaywrightBrowsers --no-daemon --console=plain
./gradlew :jsyn:test --no-daemon --console=plain -DsynausonRepoDir=/tmp/synauson-repo
```

Iterate on any failures here — this loop is fast and free compared to real CI. Do not proceed to Step 2 until this passes with 0 failures, 0 skips for every test added in Tasks 4, 5, and 8 (skips are only acceptable if the container genuinely lacks `short_speech.wav`/models, which it shouldn't since `synauson-repo` was copied in directly).

- [ ] **Step 2: Push and watch real CI**

```bash
cd jsyn
git push origin main
gh run list --repo synauson/jsyn --limit 1
gh run watch --repo synauson/jsyn <run-id>
```

Windows has no equivalent faithful local reproduction available (unlike Linux, there's no cheap disposable-container loop) — iterate on Windows failures directly against real CI runs.

- [ ] **Step 3: Confirm all four jobs green**

```bash
gh api repos/synauson/jsyn/actions/runs/<run-id>/jobs --jq '.jobs[] | "\(.name): \(.conclusion)"'
```

Expected: `java-compile: success`, `test-linux: success`, `test-windows: success`, `publish-snapshot: success`.

- [ ] **Step 4: Verify the new tests actually ran (not skipped) in CI**

```bash
gh run view --repo synauson/jsyn <run-id> --log | grep -E "SipMediaE2eIT|SipDtmfE2eIT|WebRtcMediaE2eIT" | grep -iE "PASSED|SKIPPED|FAILED"
```

Expected: every test in all three new classes shows `PASSED` on both `test-linux` and `test-windows` — zero `SKIPPED` entries. A skip here means `short_speech.wav`/`models/` weren't found in the CI checkout, which would silently reintroduce exactly the kind of gap this whole effort exists to close; if seen, treat it as a real failure and fix the path, not an acceptable outcome.
