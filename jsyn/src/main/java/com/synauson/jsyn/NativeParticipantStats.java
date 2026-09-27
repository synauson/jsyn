package com.synauson.jsyn;

/**
 * Immutable snapshot of native participant ring buffer statistics.
 *
 * <p>Values are sourced from the ring buffer headers via
 * {@code NativeBridge.getNativeParticipantStats(npHandle)}, which returns a 7-element
 * {@code long[]} in the order documented here.
 *
 * @since 0.1.0
 */
public final class NativeParticipantStats {
    /** Total bytes written to the ingress ring (monotonically increasing). */
    public final long bytesWritten;
    /** Total bytes read from the egress ring (monotonically increasing). */
    public final long bytesRead;
    /** Number of times the ingress ring writer wrapped without the reader keeping up. */
    public final long ingressOverruns;
    /** Number of times the egress ring writer wrapped without the reader keeping up. */
    public final long egressOverruns;
    /** Number of times the ingress ring reader found no data available. */
    public final long ingressUnderruns;
    /** Current bytes queued in the ingress ring ({@code writer_pos - reader_pos}). */
    public final long ingressRingUtilization;
    /** Current bytes queued in the egress ring ({@code writer_pos - reader_pos}). */
    public final long egressRingUtilization;

    /**
     * Construct an immutable stats snapshot.
     *
     * @param bytesWritten          total ingress bytes written
     * @param bytesRead             total egress bytes read
     * @param ingressOverruns       ingress wrap-without-reader count
     * @param egressOverruns        egress wrap-without-reader count
     * @param ingressUnderruns      ingress empty-read count
     * @param ingressRingUtilization current ingress queued bytes
     * @param egressRingUtilization  current egress queued bytes
     */
    public NativeParticipantStats(long bytesWritten, long bytesRead,
                                   long ingressOverruns, long egressOverruns,
                                   long ingressUnderruns,
                                   long ingressRingUtilization, long egressRingUtilization) {
        this.bytesWritten = bytesWritten;
        this.bytesRead = bytesRead;
        this.ingressOverruns = ingressOverruns;
        this.egressOverruns = egressOverruns;
        this.ingressUnderruns = ingressUnderruns;
        this.ingressRingUtilization = ingressRingUtilization;
        this.egressRingUtilization = egressRingUtilization;
    }
}
