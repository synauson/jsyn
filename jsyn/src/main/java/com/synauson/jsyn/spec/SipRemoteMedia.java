package com.synauson.jsyn.spec;

import java.util.Objects;

/**
 * The media a SIP peer negotiated in its SDP: where it receives RTP, the codec and DTMF
 * payload type, and its SRTP key.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code SipRemoteMediaJson}
 * ({@code serde(rename_all = "camelCase")}).
 *
 * @since 1.2.0
 * @see SipConnectionSpec
 */
public final class SipRemoteMedia {
    /** Address the peer receives RTP on (SDP {@code c=} line). */
    public final String remoteIp;

    /** Port the peer receives RTP on (SDP {@code m=} line); RTCP goes to the next port. */
    public final int remoteRtpPort;

    /** Codec string. One of {@code "PCMU"}, {@code "PCMA"}, {@code "OPUS"}. */
    public final String codec;

    /** RFC 4733 DTMF payload type; {@code 0} disables DTMF, otherwise {@code 96..127}. */
    public final int dtmfPayloadType;

    /**
     * The peer's 30-byte SRTP master key from its {@code a=crypto} line, which decrypts what it
     * sends. Set exactly when the reservation carried our key.
     */
    public final byte[] srtpKey;

    private SipRemoteMedia(Builder b) {
        this.remoteIp = Objects.requireNonNull(b.remoteIp, "remoteIp");
        this.remoteRtpPort = b.remoteRtpPort;
        this.codec = Objects.requireNonNull(b.codec, "codec");
        this.dtmfPayloadType = b.dtmfPayloadType;
        this.srtpKey = b.srtpKey == null ? null : b.srtpKey.clone();
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link SipRemoteMedia}. {@code remoteIp}, {@code remoteRtpPort} and
     * {@code codec} are required.
     *
     * @since 1.2.0
     */
    public static final class Builder {
        private String remoteIp;
        private int remoteRtpPort;
        private String codec;
        private int dtmfPayloadType;
        private byte[] srtpKey;

        /**
         * Set the peer's RTP address. Required.
         *
         * @param remoteIp dotted-quad IP, IPv6 literal, or hostname; non-null
         * @return this builder
         */
        public Builder remoteIp(String remoteIp) { this.remoteIp = remoteIp; return this; }

        /**
         * Set the peer's RTP port. Required.
         *
         * @param port remote RTP UDP port, in {@code [1, 65535]}
         * @return this builder
         */
        public Builder remoteRtpPort(int port) { this.remoteRtpPort = port; return this; }

        /**
         * Set the negotiated codec. Required.
         *
         * @param codec one of {@code "PCMU"}, {@code "PCMA"}, {@code "OPUS"}; non-null
         * @return this builder
         */
        public Builder codec(String codec) { this.codec = codec; return this; }

        /**
         * Set the negotiated RFC 4733 DTMF payload type.
         *
         * @param pt payload type; {@code 0} disables DTMF
         * @return this builder
         */
        public Builder dtmfPayloadType(int pt) { this.dtmfPayloadType = pt; return this; }

        /**
         * Set the peer's SRTP master key.
         *
         * @param key the peer's 30-byte SRTP master key, or {@code null} for plain RTP
         * @return this builder
         */
        public Builder srtpKey(byte[] key) { this.srtpKey = key; return this; }

        /**
         * Materialise an immutable {@link SipRemoteMedia}.
         *
         * @return the configured remote media
         * @throws NullPointerException if {@code remoteIp} or {@code codec} is null
         */
        public SipRemoteMedia build() { return new SipRemoteMedia(this); }
    }
}
