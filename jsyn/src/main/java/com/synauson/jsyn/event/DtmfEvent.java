package com.synauson.jsyn.event;

/**
 * DTMF digit received from a SIP participant.
 *
 * <p>Emitted on the DTMF event stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamDtmfEvents}. Constructed
 * directly by the native layer; the constructor signature
 * {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZ)V} matches the
 * cached signature in {@code synauson-jni/src/jni_cache.rs DtmfEventClasses}.
 *
 * @since 0.1.0
 */
public final class DtmfEvent {
    /** Conference ID this event was emitted for. */
    public final String conferenceId;
    /** Participant ID this event was emitted for. */
    public final String participantId;
    /** Single-character digit string: {@code "0"}-{@code "9"}, {@code "*"}, {@code "#"}, or {@code "A"}-{@code "D"}. */
    public final String digit;
    /** Digit duration in milliseconds, as reported by the RTP event packet. */
    public final long durationMs;
    /** {@code true} if received in-band (RFC 4733 RTP event), {@code false} if out-of-band. */
    public final boolean inBand;

    /**
     * Construct a DTMF event. Invoked from the JNI layer; rarely called from application code.
     *
     * @param conferenceId  conference identifier; non-null
     * @param participantId participant identifier; non-null
     * @param digit         single-character digit string
     * @param durationMs    digit duration in milliseconds
     * @param inBand        {@code true} if received in-band via RFC 4733
     */
    public DtmfEvent(String conferenceId, String participantId, String digit,
                     long durationMs, boolean inBand) {
        this.conferenceId = conferenceId;
        this.participantId = participantId;
        this.digit = digit;
        this.durationMs = durationMs;
        this.inBand = inBand;
    }
}
