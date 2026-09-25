package com.synauson.jsyn.spec;

import com.synauson.jsyn.internal.Args;
import org.jspecify.annotations.Nullable;

/**
 * Spec for reserving a SIP participant's local RTP/RTCP ports before the peer's media is
 * known: the first half of an outbound call, where our SDP offer must name our port before
 * the answer names the peer's address and codec.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code SipReservationJson} in the JNI
 * layer ({@code serde(rename_all = "camelCase")}): {@code participantId} and an optional
 * {@code ourSrtpKey}.
 *
 * @since 1.2.0
 * @see com.synauson.jsyn.participant.Conference#reserveSipParticipant(SipReservationSpec)
 */
public final class SipReservationSpec {
    /** Participant identifier the reservation, and later the participant, is known by. */
    public final String participantId;

    /**
     * Our 30-byte SRTP master key (AES_CM_128_HMAC_SHA1_80: 16-byte key + 14-byte salt),
     * advertised in the offer's {@code a=crypto} line and used to encrypt what we send;
     * {@code null} for plain RTP. When set, the connect call must carry the peer's key.
     */
    public final byte @Nullable [] ourSrtpKey;

    private SipReservationSpec(Builder b) {
        this.participantId = Args.notNull(b.participantId, "participantId");
        this.ourSrtpKey = b.ourSrtpKey == null ? null : b.ourSrtpKey.clone();
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link SipReservationSpec}. {@code participantId} is required.
     *
     * @since 1.2.0
     */
    public static final class Builder {
        private @Nullable String participantId;
        private byte @Nullable [] ourSrtpKey;

        /**
         * Set the participant ID. Required.
         *
         * @param id participant identifier; non-null
         * @return this builder
         */
        public Builder participantId(String id) { this.participantId = id; return this; }

        /**
         * Protect the call with SRTP, using {@code key} for what we send.
         *
         * @param key our 30-byte SRTP master key, or {@code null} for plain RTP
         * @return this builder
         */
        public Builder ourSrtpKey(byte @Nullable [] key) { this.ourSrtpKey = key; return this; }

        /**
         * Materialise an immutable {@link SipReservationSpec}.
         *
         * @return the configured spec
         * @throws com.synauson.jsyn.exception.InvalidArgumentException naming every required
         *         field that is missing
         */
        public SipReservationSpec build() {
            Args.required("SipReservationSpec")
                .field("participantId", participantId)
                .validate();
            return new SipReservationSpec(this);
        }
    }
}
