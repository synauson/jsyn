package com.synauson.jsyn.participant;

import com.synauson.jsyn.SipStats;
import com.synauson.jsyn.internal.NativeBridge;
import java.util.Objects;

/**
 * Handle to a SIP participant returned by
 * {@link com.synauson.jsyn.participant.Conference#addSipParticipant}.
 *
 * <p>Carries the participant ID and the locally allocated RTP port number that was
 * negotiated during participant construction. Use {@link #localRtpPort()} to relay this
 * port back to the SIP signaling layer for the remote peer's SDP answer.
 *
 * <p>Provides DTMF send via {@link #sendDtmf(char, int)} and live quality statistics
 * via {@link #stats()}.
 *
 * @since 0.1.0
 */
public final class SipParticipantHandle {
    private final long runtimeHandle;
    private final String conferenceId;
    private final String participantId;
    private final int localRtpPort;

    /**
     * Construct a SIP participant handle. Invoked by
     * {@link Conference#addSipParticipant} after the native call returns; rarely
     * constructed from application code.
     *
     * @param runtimeHandle opaque runtime handle from {@code NativeBridge.initRuntime}
     * @param conferenceId  conference identifier; non-null
     * @param participantId participant identifier; non-null
     * @param localRtpPort  locally allocated RTP receive port for this participant
     * @throws NullPointerException if {@code conferenceId} or {@code participantId} is null
     */
    public SipParticipantHandle(long runtimeHandle, String conferenceId,
                                 String participantId, int localRtpPort) {
        this.runtimeHandle = runtimeHandle;
        this.conferenceId = Objects.requireNonNull(conferenceId, "conferenceId");
        this.participantId = Objects.requireNonNull(participantId, "participantId");
        this.localRtpPort = localRtpPort;
    }

    /**
     * Returns the participant's assigned identifier.
     *
     * @return the participant ID
     */
    public String id() { return participantId; }

    /**
     * Returns the locally allocated RTP receive port for this participant's media stream.
     *
     * @return the RTP port, in {@code [1, 65535]}
     */
    public int localRtpPort() { return localRtpPort; }

    /**
     * Send an in-band DTMF digit to the remote peer.
     *
     * <p>Accepted digits: {@code '0'-'9'}, {@code '*'}, {@code '#'}, {@code 'A'-'D'}
     * (case-insensitive). The character is converted to the RFC 4733 numeric event code
     * (0-15) before calling the native layer.
     *
     * @param digit      the DTMF digit character
     * @param durationMs digit duration in milliseconds (clamped to {@code [70, 500]} by the server)
     * @throws IllegalArgumentException if {@code digit} is not a valid DTMF character
     * @throws com.synauson.jsyn.exception.NotFoundException if the participant no longer exists
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if DTMF is disabled on this participant
     */
    public void sendDtmf(char digit, int durationMs) {
        long digitNumber = charToDigitNumber(digit);
        NativeBridge.sendDtmf(runtimeHandle, conferenceId, participantId,
                              digitNumber, (long) durationMs);
    }

    private static long charToDigitNumber(char digit) {
        if (digit >= '0' && digit <= '9') return digit - '0';
        switch (Character.toUpperCase(digit)) {
            case '*': return 10;
            case '#': return 11;
            case 'A': return 12;
            case 'B': return 13;
            case 'C': return 14;
            case 'D': return 15;
            default:  throw new IllegalArgumentException("invalid DTMF digit: '" + digit + "'");
        }
    }

    /**
     * Retrieve per-participant RTP quality statistics.
     *
     * @return a fresh stats snapshot
     * @throws com.synauson.jsyn.exception.NotFoundException if the participant no longer exists
     */
    public SipStats stats() {
        String json = NativeBridge.getSipStats(runtimeHandle, conferenceId, participantId);
        return SipStats.fromJson(json);
    }
}
