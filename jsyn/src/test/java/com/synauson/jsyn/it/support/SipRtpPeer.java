package com.synauson.jsyn.it.support;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    /** RFC 3550/4733 packetization interval used for both audio frames and DTMF pacing. */
    private static final int PACKETIZATION_INTERVAL_MS = 20;
    /** Samples per packetization interval at the 8kHz RTP clock shared by PCMU audio and telephone-event. */
    private static final int SAMPLES_PER_PACKET_8KHZ = 160;

    private final DatagramSocket socket;
    private final long ssrc;
    // RFC 3550: one SSRC (one sender) uses a single monotonically-incrementing sequence-number
    // space across every packet it emits, audio and DTMF alike — not two independent counters.
    private final AtomicInteger sequenceNumber = new AtomicInteger(1000);
    private final List<RtpPacket> captured = new CopyOnWriteArrayList<>();
    private final Set<Integer> capturedSourcePorts = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<Throwable> receiveLoopFailure = new AtomicReference<>();
    private final Thread receiveThread;

    private volatile InetAddress targetAddress;
    private volatile int[] targetPorts = new int[0];
    private volatile Thread toneThread;

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
        setTargets(host, new int[] {port});
    }

    /**
     * Deliver every packet to each of {@code ports}. Lets a test send early
     * media before synauson has told it which of a few candidate ports its
     * participant will be given; call {@link #setTarget} once it knows.
     */
    public void setTargets(String host, int[] ports) throws IOException {
        this.targetAddress = InetAddress.getByName(host);
        this.targetPorts = ports.clone();
    }

    /**
     * Start streaming a continuous {@code freqHz} sine (peak 12000, before
     * mu-law) as 20 ms PCMU frames on a background thread, paced against the
     * wall clock like a real endpoint. Runs until {@link #stopTone()} or
     * {@link #close()}.
     */
    public synchronized void startTone(double freqHz) {
        requireTarget();
        stopTone();
        Thread t = new Thread(() -> streamTone(freqHz), "sip-rtp-peer-tone-" + socket.getLocalPort());
        t.setDaemon(true);
        toneThread = t;
        t.start();
    }

    /** Stop a tone started with {@link #startTone}; a no-op when none is running. */
    public synchronized void stopTone() {
        Thread t = toneThread;
        toneThread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void streamTone(double freqHz) {
        byte[] frame = new byte[SAMPLES_PER_PACKET_8KHZ * 2];
        long sample = 0;
        long start = System.nanoTime();
        try {
            for (long f = 0; toneThread == Thread.currentThread(); f++) {
                for (int i = 0; i < SAMPLES_PER_PACKET_8KHZ; i++, sample++) {
                    short s = (short) (12_000.0 * Math.sin(2.0 * Math.PI * freqHz * sample / 8000.0));
                    frame[i * 2] = (byte) (s & 0xFF);
                    frame[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
                }
                sendAudioFrame(frame);
                long sleepNanos = start + (f + 1) * PACKETIZATION_INTERVAL_MS * 1_000_000L - System.nanoTime();
                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                }
            }
        } catch (InterruptedException e) {
            // stopTone()
        } catch (IOException e) {
            if (running.get()) {
                receiveLoopFailure.compareAndSet(null, e);
            }
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[2048];
        while (running.get()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                captured.add(RtpPacket.parse(packet.getData(), packet.getLength()));
                capturedSourcePorts.add(packet.getPort());
            } catch (SocketTimeoutException expected) {
                // normal: re-check the running flag and loop again
            } catch (RuntimeException malformedPacket) {
                // A real peer can legitimately send something RtpPacket.parse doesn't
                // handle (RTCP, header extensions) — record it but keep the loop alive
                // rather than silently dying and leaving captured() frozen forever.
                receiveLoopFailure.set(malformedPacket);
            } catch (IOException e) {
                if (running.get()) {
                    receiveLoopFailure.set(e);
                }
            }
        }
    }

    /** Non-null if the background receive loop hit an error processing a packet. */
    public Throwable receiveLoopFailure() {
        return receiveLoopFailure.get();
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
        int seq = sequenceNumber.getAndIncrement();
        long timestamp = seq * (long) SAMPLES_PER_PACKET_8KHZ;
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

        // RFC 3550: this event's packets share the same monotonic sequence-number space as
        // this peer's audio packets (one SSRC, one sequence-number space), and per RFC 4733
        // section 2.5.1.3 the RTP timestamp is fixed for every packet of the same event —
        // both derived from the shared 8kHz sample clock, not a DTMF-only counter.
        int seq = sequenceNumber.getAndIncrement();
        long eventTimestamp = seq * (long) SAMPLES_PER_PACKET_8KHZ;
        boolean first = true;
        for (int elapsed = SAMPLES_PER_PACKET_8KHZ; elapsed < totalDurationSamples; elapsed += SAMPLES_PER_PACKET_8KHZ) {
            byte[] payload = buildDtmfPayload(eventNumber, false, volume, elapsed);
            send(RtpPacket.build(dtmfPayloadType, first, seq, eventTimestamp, ssrc, payload));
            first = false;
            seq = sequenceNumber.getAndIncrement();
            sleepPacketizationInterval();
        }
        byte[] endPayload = buildDtmfPayload(eventNumber, true, volume, totalDurationSamples);
        send(RtpPacket.build(dtmfPayloadType, first, seq, eventTimestamp, ssrc, endPayload));
        sleepPacketizationInterval(); // pace the trailing gap too, same as every other packet in the event
    }

    private static void sleepPacketizationInterval() throws IOException {
        try {
            Thread.sleep(PACKETIZATION_INTERVAL_MS);
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
        if (targetAddress == null || targetPorts.length == 0) {
            throw new IllegalStateException("setTarget(host, port) must be called before sending");
        }
    }

    private void send(byte[] wire) throws IOException {
        for (int port : targetPorts) {
            socket.send(new DatagramPacket(wire, wire.length, targetAddress, port));
        }
    }

    /** Snapshot of every RTP packet received at this peer's bound port so far. */
    public List<RtpPacket> capturedPackets() {
        return new ArrayList<>(captured);
    }

    /**
     * The UDP source port of every RTP packet captured so far. With symmetric RTP
     * (RFC 4961) this is exactly synauson's local RTP port for the participant.
     */
    public Set<Integer> capturedSourcePorts() {
        return new TreeSet<>(capturedSourcePorts);
    }

    /** Forget every packet captured so far, to measure only what arrives next. */
    public void clearCaptured() {
        captured.clear();
        capturedSourcePorts.clear();
    }

    @Override
    public void close() {
        stopTone();
        running.set(false);
        try {
            receiveThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        socket.close();
    }
}
