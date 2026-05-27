package com.synauson.jsyn.participant;

import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.NativeParticipantStats;
import com.synauson.jsyn.internal.NativeBridge;
import com.synauson.jsyn.internal.NativeParticipantNativeHandle;
import com.synauson.jsyn.internal.NativeResource;
import com.synauson.jsyn.ring.SpscRing;

/**
 * In-process audio participant with direct ring-buffer I/O.
 *
 * <p>Extends {@link NativeResource} so the underlying native participant is freed
 * deterministically via try-with-resources or garbage collection (Cleaner safety net).
 *
 * <p>Audio flows in two directions:
 * <ul>
 *   <li><b>Ingress</b> (Java → native): call {@link #write} to push PCM audio into the
 *       conference. The native pipeline reads from the ingress ring and mixes it into the
 *       conference audio graph.</li>
 *   <li><b>Egress</b> (native → Java): call {@link #read} to pull the conference's mixed
 *       audio out for this participant. The native pipeline writes to the egress ring.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class NativeParticipant extends NativeResource {
    private final String conferenceId;
    private final String participantId;
    private final long npHandle;
    private final NativeAudioFormat format;
    private final SpscRing ingressRing;
    private final SpscRing egressRing;

    /**
     * Construct a native participant from the raw handle returned by the JNI layer.
     * Invoked by {@link com.synauson.jsyn.participant.Conference#addNativeParticipant};
     * not typically called from application code.
     *
     * @param conferenceId  conference identifier
     * @param participantId participant identifier
     * @param nativeHandle  raw JNI result containing the handle and ring buffers
     */
    public NativeParticipant(String conferenceId, String participantId,
                              NativeParticipantNativeHandle nativeHandle) {
        super(() -> NativeBridge.closeNativeParticipant(nativeHandle.handle));
        this.conferenceId = conferenceId;
        this.participantId = participantId;
        this.npHandle = nativeHandle.handle;
        this.format = NativeAudioFormat.fromId(nativeHandle.formatId);
        this.ingressRing = new SpscRing(nativeHandle.ingressRing);
        this.egressRing  = new SpscRing(nativeHandle.egressRing);
    }

    /**
     * Returns the ID of the conference this participant belongs to.
     *
     * @return the conference identifier
     */
    public String conferenceId() { return conferenceId; }

    /**
     * Returns the participant's assigned identifier.
     *
     * @return the participant ID
     */
    public String id() { return participantId; }

    /**
     * Returns the audio format negotiated for both ingress and egress ring buffers.
     *
     * @return the ring buffer audio format
     */
    public NativeAudioFormat format() { return format; }

    /**
     * Write audio frames into the ingress ring (Java → native).
     *
     * @param src byte array of PCM/G.711 samples in the participant's {@link #format()}
     * @param off offset into {@code src}
     * @param len number of bytes to write
     * @return bytes actually written; may be less than {@code len} if the ring is full
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     */
    public int write(byte[] src, int off, int len) {
        requireOpen();
        return ingressRing.write(src, off, len);
    }

    /**
     * Read conference audio frames from the egress ring (native → Java).
     *
     * @param dst destination byte array
     * @param off offset into {@code dst}
     * @param len maximum bytes to read
     * @return bytes actually read; may be less than {@code len} if the ring is empty
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     */
    public int read(byte[] dst, int off, int len) {
        requireOpen();
        return egressRing.read(dst, off, len);
    }

    /**
     * Retrieve ring buffer statistics. The returned snapshot is a point-in-time view;
     * counters are monotonically increasing.
     *
     * @return a fresh stats snapshot
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     */
    public NativeParticipantStats stats() {
        requireOpen();
        long[] raw = NativeBridge.getNativeParticipantStats(npHandle);
        return new NativeParticipantStats(raw[0], raw[1], raw[2], raw[3], raw[4], raw[5], raw[6]);
    }
}
