package com.synauson.jsyn.participant;

import java.util.Objects;

/**
 * A SIP participant's local RTP/RTCP ports, reserved by
 * {@link Conference#reserveSipParticipant} for an outbound call's SDP offer.
 *
 * <p>Both sockets are bound when the reservation is returned, so media the peer sends early
 * queues until {@link Conference#connectSipParticipant} starts the participant on these same
 * ports; nothing runs before then. A reservation is not a participant: it does not appear in
 * {@link Conference#state()}. It lasts until it is connected, removed with
 * {@link Conference#removeParticipant(String)}, or the conference terminates. A connect that
 * fails after its arguments were accepted also releases it.
 *
 * @since 1.2.0
 */
public final class SipReservation {
    private final String participantId;
    private final int localRtpPort;
    private final int localRtcpPort;

    /**
     * Construct a reservation. Invoked by {@link Conference#reserveSipParticipant} after the
     * native call returns; rarely constructed from application code.
     *
     * @param participantId the reserved participant ID; non-null
     * @param localRtpPort  the reserved RTP port
     * @param localRtcpPort the reserved RTCP port
     */
    public SipReservation(String participantId, int localRtpPort, int localRtcpPort) {
        this.participantId = Objects.requireNonNull(participantId, "participantId");
        this.localRtpPort = localRtpPort;
        this.localRtcpPort = localRtcpPort;
    }

    /**
     * Returns the reserved participant's identifier.
     *
     * @return the participant ID
     */
    public String id() { return participantId; }

    /**
     * Returns the reserved RTP port: the port for the SDP offer's {@code m=} line.
     *
     * @return the RTP port, always even
     */
    public int localRtpPort() { return localRtpPort; }

    /**
     * Returns the reserved RTCP port.
     *
     * @return the RTCP port, always {@link #localRtpPort()} + 1
     */
    public int localRtcpPort() { return localRtcpPort; }

    @Override
    public String toString() {
        return "SipReservation{" + participantId + ", rtp=" + localRtpPort
                + ", rtcp=" + localRtcpPort + "}";
    }
}
