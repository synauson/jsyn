package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Spec for adding a WebRTC participant.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code WebRtcParticipantSpec}:
 * snake_case field names ({@code participant_id}, {@code sdp_offer}, {@code stun_server},
 * {@code jitter_buffer_ms}, {@code vad}, {@code smart_turn}).
 *
 * <p>The caller must obtain the browser's SDP offer through the application signaling
 * channel before constructing this spec. The native runtime generates a corresponding
 * SDP answer that is returned in the participant handle and must be relayed back to the
 * browser.
 *
 * @since 0.1.0
 */
public final class WebRtcParticipantSpec {
    /** Participant identifier; serialized as {@code participant_id}. */
    @SerializedName("participant_id")
    public final String participantId;

    /** SDP offer received from the browser; serialized as {@code sdp_offer}. */
    @SerializedName("sdp_offer")
    public final String sdpOffer;

    /** STUN server URI for ICE; serialized as {@code stun_server}. */
    @SerializedName("stun_server")
    public final String stunServer;

    /** GStreamer webrtcbin jitter buffer latency in milliseconds. */
    @SerializedName("jitter_buffer_ms")
    public final int jitterBufferMs;

    /** Optional VAD configuration; {@code null} disables VAD detection. */
    public final VadConfig vad;

    /** Optional SmartTurn configuration; {@code null} disables SmartTurn detection. */
    @SerializedName("smart_turn")
    public final SmartTurnConfig smartTurn;

    private WebRtcParticipantSpec(Builder b) {
        this.participantId = Objects.requireNonNull(b.participantId, "participantId");
        this.sdpOffer = Objects.requireNonNull(b.sdpOffer, "sdpOffer");
        this.stunServer = Objects.requireNonNull(b.stunServer, "stunServer");
        this.jitterBufferMs = b.jitterBufferMs;
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
     * Fluent builder for {@link WebRtcParticipantSpec}. {@code participantId},
     * {@code sdpOffer}, and {@code stunServer} are required.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private String participantId;
        private String sdpOffer;
        private String stunServer;
        private int jitterBufferMs;
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
         * Set the SDP offer received from the browser. Required.
         *
         * @param offer SDP offer string; non-null
         * @return this builder
         */
        public Builder sdpOffer(String offer) { this.sdpOffer = offer; return this; }

        /**
         * Set the STUN server URI for ICE. Required.
         *
         * @param stun STUN URI (e.g. {@code "stun://stun.l.google.com:19302"}); non-null
         * @return this builder
         */
        public Builder stunServer(String stun) { this.stunServer = stun; return this; }

        /**
         * Set the GStreamer webrtcbin jitter buffer latency.
         *
         * @param ms jitter buffer latency in milliseconds; non-negative
         * @return this builder
         */
        public Builder jitterBufferMs(int ms) { this.jitterBufferMs = ms; return this; }

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
         * Materialise an immutable {@link WebRtcParticipantSpec}.
         *
         * @return the configured spec
         * @throws NullPointerException if any required field is null
         */
        public WebRtcParticipantSpec build() { return new WebRtcParticipantSpec(this); }
    }
}
