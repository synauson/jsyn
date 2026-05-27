package com.synauson.jsyn.spec;

import java.util.Objects;

/**
 * Spec for adding a SIP participant.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code SipParticipantJson}
 * in the JNI layer, which uses {@code serde(rename_all = "camelCase")}. All Java field
 * names are therefore camelCase to match Gson's default serialization.
 *
 * <p>A SIP participant terminates an RTP/RTCP session toward {@code remoteIp:remoteRtpPort}.
 * The locally bound port is allocated by the server inside the configured
 * {@code rtpPortMin}/{@code rtpPortMax} range and returned in the participant handle.
 *
 * @since 0.1.0
 */
public final class SipParticipantSpec {
    /** Participant identifier; serialized as {@code participantId}. */
    public final String participantId;

    /** Remote host IP or hostname. */
    public final String remoteIp;

    /** Remote RTP port. */
    public final int remoteRtpPort;

    /** Codec string. One of {@code "PCMU"}, {@code "PCMA"}, {@code "OPUS"}. */
    public final String codec;

    /** RFC 4733 DTMF payload type; {@code 0} disables DTMF. */
    public final int dtmfPayloadType;

    /** Optional SRTP configuration; {@code null} = plaintext RTP. */
    public final SrtpConfig srtp;

    /** Optional VAD configuration; {@code null} disables VAD detection. */
    public final VadConfig vad;

    /** Optional SmartTurn configuration; {@code null} disables SmartTurn detection. */
    public final SmartTurnConfig smartTurn;

    private SipParticipantSpec(Builder b) {
        this.participantId = Objects.requireNonNull(b.participantId, "participantId");
        this.remoteIp = Objects.requireNonNull(b.remoteIp, "remoteIp");
        this.remoteRtpPort = b.remoteRtpPort;
        this.codec = Objects.requireNonNull(b.codec, "codec");
        this.dtmfPayloadType = b.dtmfPayloadType;
        this.srtp = b.srtp;
        this.vad = b.vad;
        this.smartTurn = b.smartTurn;
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link SipParticipantSpec}. {@code participantId}, {@code remoteIp},
     * and {@code codec} are required.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private String participantId;
        private String remoteIp;
        private int remoteRtpPort;
        private String codec;
        private int dtmfPayloadType;
        private SrtpConfig srtp;
        private VadConfig vad;
        private SmartTurnConfig smartTurn;

        /**
         * Set the participant ID. Required.
         *
         * @param id participant identifier; non-null
         * @return this builder
         */
        public Builder participantId(String id) { this.participantId = id; return this; }

        /**
         * Set the remote host IP or hostname. Required.
         *
         * @param remoteIp dotted-quad IP, IPv6 literal, or hostname; non-null
         * @return this builder
         */
        public Builder remoteIp(String remoteIp) { this.remoteIp = remoteIp; return this; }

        /**
         * Set the remote RTP port. Required.
         *
         * @param port remote RTP UDP port, in {@code [1, 65535]}
         * @return this builder
         */
        public Builder remoteRtpPort(int port) { this.remoteRtpPort = port; return this; }

        /**
         * Set the codec. Required.
         *
         * @param codec one of {@code "PCMU"}, {@code "PCMA"}, {@code "OPUS"}; non-null
         * @return this builder
         */
        public Builder codec(String codec) { this.codec = codec; return this; }

        /**
         * Set the RFC 4733 DTMF payload type.
         *
         * @param pt payload type; {@code 0} disables DTMF
         * @return this builder
         */
        public Builder dtmfPayloadType(int pt) { this.dtmfPayloadType = pt; return this; }

        /**
         * Enable SRTP for this participant.
         *
         * @param srtp SRTP key material, or {@code null} for plaintext RTP
         * @return this builder
         */
        public Builder srtp(SrtpConfig srtp) { this.srtp = srtp; return this; }

        /**
         * Enable VAD detection on this participant's audio stream.
         *
         * @param vad VAD configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder vad(VadConfig vad) { this.vad = vad; return this; }

        /**
         * Enable SmartTurn detection on this participant's audio stream.
         *
         * @param st SmartTurn configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder smartTurn(SmartTurnConfig st) { this.smartTurn = st; return this; }

        /**
         * Materialise an immutable {@link SipParticipantSpec}.
         *
         * @return the configured spec
         * @throws NullPointerException if any required field is null
         */
        public SipParticipantSpec build() { return new SipParticipantSpec(this); }
    }
}
