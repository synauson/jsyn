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
     * <p>Packets are paced over real wall-clock time at the same 20ms
     * interval they represent, rather than fired back-to-back. This mirrors
     * gst-plugins-good's {@code rtpdtmfsrc} (gst/dtmf/gstrtpdtmfsrc.c),
     * which is a live {@code GstBaseSrc} that blocks on the pipeline clock
     * between packets ({@code gst_clock_id_wait} in
     * {@code gst_rtp_dtmf_src_create}) — a real DTMF sender never bursts an
     * entire event's packets at once.
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
            sleepPacketizationInterval();
        }
        byte[] endPayload = buildDtmfPayload(eventNumber, true, volume, totalDurationSamples);
        send(RtpPacket.build(dtmfPayloadType, first, seq, eventTimestamp, ssrc, endPayload));
        sleepPacketizationInterval(); // pace the trailing gap too, same as every other packet in the event
    }

    private static void sleepPacketizationInterval() throws IOException {
        try {
            Thread.sleep(20); // matches the 20ms RTP clock step used above
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while pacing DTMF packet send", e);
        }
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
