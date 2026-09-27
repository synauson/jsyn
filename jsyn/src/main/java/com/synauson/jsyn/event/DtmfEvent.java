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
    /**
     * Digit duration in milliseconds. Always {@code 0} for received events:
     * neither the RFC 4733 depayloader ({@code rtpdtmfdepay}) nor the
     * in-band tone detector ({@code dtmfdetect}) report a duration for a
     * detected digit (see {@code synauson-core/src/conference/actor.rs}'s
     * {@code handle_dtmf_bus_message}). Present for symmetry with
     * {@code SipParticipantHandle#sendDtmf}'s {@code durationMs} parameter,
     * which does control the outbound event's duration.
     */
    public final long durationMs;
    /**
     * {@code true} if detected in-band via audio-domain tone detection
     * ({@code dtmfdetect}), {@code false} if received via the RFC 4733
     * RTP event stream ({@code rtpdtmfdepay}) — i.e. RFC 4733 is
     * out-of-band, matching standard telecom terminology. This is the
     * opposite of what the name might suggest at a glance; verified
     * against {@code synauson-core}'s {@code handle_dtmf_bus_message}.
     */
    public final boolean inBand;

    /**
     * Construct a DTMF event. Invoked from the JNI layer; rarely called from application code.
     *
     * @param conferenceId  conference identifier; non-null
     * @param participantId participant identifier; non-null
     * @param digit         single-character digit string
     * @param durationMs    digit duration in milliseconds; always {@code 0} for received events
     * @param inBand        {@code true} if detected in-band via {@code dtmfdetect}; {@code false}
     *                      for RFC 4733 (out-of-band)
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
