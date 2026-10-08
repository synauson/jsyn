package com.synauson.jsyn.spec;

import com.synauson.jsyn.internal.Args;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Spec for adding a WebRTC participant.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code WebRtcParticipantSpec}:
 * snake_case field names ({@code participant_id}, {@code sdp_offer}, {@code stun_server},
 * {@code jitter_buffer_ms}, {@code vad}, {@code turn_detection}, and the per-participant
 * options below). Unset options are omitted from the JSON.
 *
 * <p>The WebRTC options are all optional. An unset option takes the runtime default from
 * {@link com.synauson.jsyn.JSynConfig} ({@code webrtcStunServer},
 * {@code webrtcJitterBufferMs}, {@code webrtcIcePortRange}), or the built-in default on
 * its builder method. The native runtime validates them and throws
 * {@link com.synauson.jsyn.exception.InvalidArgumentException} for an invalid one.
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

    /**
     * STUN server URI for ICE; serialized as {@code stun_server}. {@code null} uses the
     * runtime default; an empty string turns STUN off.
     */
    @SerializedName("stun_server")
    public final @Nullable String stunServer;

    /**
     * Receive jitter buffer latency in milliseconds; serialized as {@code jitter_buffer_ms}.
     * {@code null} uses the runtime default.
     */
    @SerializedName("jitter_buffer_ms")
    public final @Nullable Integer jitterBufferMs;

    /** TURN server URIs; serialized as {@code turn_servers}. {@code null} means none. */
    @SerializedName("turn_servers")
    public final @Nullable List<String> turnServers;

    /** ICE transport policy; {@code null} means {@link IceTransportPolicy#ALL}. */
    @SerializedName("ice_transport_policy")
    public final @Nullable IceTransportPolicy iceTransportPolicy;

    /** Lowest local port for ICE candidates; {@code null} uses the runtime default. */
    @SerializedName("ice_port_min")
    public final @Nullable Integer icePortMin;

    /** Highest local port for ICE candidates; {@code null} uses the runtime default. */
    @SerializedName("ice_port_max")
    public final @Nullable Integer icePortMax;

    /** Bitrate of the Opus audio the runtime sends, in bps; {@code null} means 64000. */
    @SerializedName("opus_bitrate")
    public final @Nullable Integer opusBitrate;

    /** Opus discontinuous transmission for the audio sent; {@code null} means on. */
    @SerializedName("opus_dtx")
    public final @Nullable Boolean opusDtx;

    /** Opus in-band forward error correction for the audio sent; {@code null} means off. */
    @SerializedName("opus_inband_fec")
    public final @Nullable Boolean opusInbandFec;

    /**
     * Expected packet loss percentage, which sizes the in-band FEC; {@code null} means 10
     * with FEC on, else 0.
     */
    @SerializedName("opus_packet_loss_percentage")
    public final @Nullable Integer opusPacketLossPercentage;

    /** Optional VAD configuration; {@code null} disables VAD detection. */
    public final @Nullable VadConfig vad;

    /**
     * Optional turn-detection configuration; {@code null} disables turn detection. Needs
     * {@link #vad} on the same participant, whose speech ends it decides on: Turn detection
     * without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     */
    @SerializedName("turn_detection")
    public final @Nullable TurnDetectionConfig turnDetection;

    /**
     * Optional streaming speech-to-text; {@code null} disables it. Needs
     * {@link #turnDetection} on the same participant: STT without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     *
     * @since 1.6.0
     */
    public final @Nullable SttConfig stt;

    /**
     * Optional speaker (text-to-speech) for
     * {@link com.synauson.jsyn.participant.Conference#speak}; {@code null} gives the
     * participant none. Needs {@link #turnDetection} on the same participant: a speaker
     * without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     *
     * @since 1.6.0
     */
    public final @Nullable TtsConfig tts;

    private WebRtcParticipantSpec(Builder b) {
        this.participantId = Args.notNull(b.participantId, "participantId");
        this.sdpOffer = Args.notNull(b.sdpOffer, "sdpOffer");
        this.stunServer = b.stunServer;
        this.jitterBufferMs = b.jitterBufferMs;
        this.turnServers = b.turnServers == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(b.turnServers));
        this.iceTransportPolicy = b.iceTransportPolicy;
        this.icePortMin = b.icePortMin;
        this.icePortMax = b.icePortMax;
        this.opusBitrate = b.opusBitrate;
        this.opusDtx = b.opusDtx;
        this.opusInbandFec = b.opusInbandFec;
        this.opusPacketLossPercentage = b.opusPacketLossPercentage;
        this.vad = b.vad;
        this.turnDetection = b.turnDetection;
        this.stt = b.stt;
        this.tts = b.tts;
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link WebRtcParticipantSpec}. {@code participantId} and
     * {@code sdpOffer} are required; every other field is optional.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private @Nullable String participantId;
        private @Nullable String sdpOffer;
        private @Nullable String stunServer;
        private @Nullable Integer jitterBufferMs;
        private @Nullable List<String> turnServers;
        private @Nullable IceTransportPolicy iceTransportPolicy;
        private @Nullable Integer icePortMin;
        private @Nullable Integer icePortMax;
        private @Nullable Integer opusBitrate;
        private @Nullable Boolean opusDtx;
        private @Nullable Boolean opusInbandFec;
        private @Nullable Integer opusPacketLossPercentage;
        private @Nullable VadConfig vad;
        private @Nullable TurnDetectionConfig turnDetection;
        private @Nullable SttConfig stt;
        private @Nullable TtsConfig tts;

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
         * Set the STUN server for this participant, of the form {@code stun://host[:port]}
         * (not the browser's {@code stun:host} form). Default: the runtime's
         * {@code webrtcStunServer}. An empty string turns STUN off.
         *
         * @param stun STUN URI, {@code ""} for none, or {@code null} for the default
         * @return this builder
         */
        public Builder stunServer(@Nullable String stun) { this.stunServer = stun; return this; }

        /**
         * Set the receive jitter buffer latency, 0 to 2000 ms. Default: the runtime's
         * {@code webrtcJitterBufferMs}. The other participants hear this participant late
         * by the same amount, because every mix of its audio waits for the buffer.
         *
         * @param ms jitter buffer latency in milliseconds
         * @return this builder
         */
        public Builder jitterBufferMs(int ms) { this.jitterBufferMs = ms; return this; }

        /**
         * Set the TURN servers, each
         * {@code turn(s)://user:password@host[:port][?transport=udp|tcp]}. A {@code :},
         * {@code @}, {@code /}, {@code ?} or {@code #} in the user or password must be
         * percent-encoded (a time-limited {@code timestamp:user} name is written
         * {@code timestamp%3Auser}). At most 8 relays: a {@code turn://} URI without a
         * transport counts as two. Default: none.
         *
         * @param uris TURN server URIs, or {@code null} for none
         * @return this builder
         */
        public Builder turnServers(@Nullable List<String> uris) {
            this.turnServers = uris == null ? null : new ArrayList<>(uris);
            return this;
        }

        /**
         * Set which ICE candidates this participant may use. Default:
         * {@link IceTransportPolicy#ALL}. {@link IceTransportPolicy#RELAY} needs a TURN
         * server.
         *
         * @param policy the policy, or {@code null} for the default
         * @return this builder
         */
        public Builder iceTransportPolicy(@Nullable IceTransportPolicy policy) {
            this.iceTransportPolicy = policy;
            return this;
        }

        /**
         * Restrict this participant's local ICE ports to {@code min} through {@code max}
         * (1 to 65535). The UDP port its media flows on is one of them. Default: the
         * runtime's {@code webrtcIcePortRange}, else any port. Each concurrent participant
         * needs one port per local address for UDP and another for ICE-TCP.
         *
         * @param min lowest port
         * @param max highest port, at least {@code min}
         * @return this builder
         */
        public Builder icePortRange(int min, int max) {
            this.icePortMin = min;
            this.icePortMax = max;
            return this;
        }

        /**
         * Set the bitrate of the Opus audio the runtime sends to this participant, 4000 to
         * 650000 bps. Default: 64000.
         *
         * @param bps bitrate in bits per second
         * @return this builder
         */
        public Builder opusBitrate(int bps) { this.opusBitrate = bps; return this; }

        /**
         * Turn Opus discontinuous transmission (tiny packets during silence) on or off for
         * the audio sent to this participant. Default: on.
         *
         * @param on whether DTX is on
         * @return this builder
         */
        public Builder opusDtx(boolean on) { this.opusDtx = on; return this; }

        /**
         * Turn Opus in-band forward error correction on or off for the audio sent to this
         * participant. Default: off.
         *
         * @param on whether in-band FEC is on
         * @return this builder
         */
        public Builder opusInbandFec(boolean on) { this.opusInbandFec = on; return this; }

        /**
         * Set the expected packet loss, 0 to 100 percent, which sizes the in-band FEC.
         * Default: 10 with FEC on, else 0 (FEC adds nothing at 0).
         *
         * @param percent expected packet loss
         * @return this builder
         */
        public Builder opusPacketLossPercentage(int percent) {
            this.opusPacketLossPercentage = percent;
            return this;
        }

        /**
         * Enable VAD detection on this participant's audio stream.
         *
         * @param vad VAD configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder vad(@Nullable VadConfig vad) { this.vad = vad; return this; }

        /**
         * Enable turn detection on this participant's audio stream.
         * Needs {@link #vad}, whose speech ends it decides on.
         *
         * @param st turn-detection configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder turnDetection(@Nullable TurnDetectionConfig st) { this.turnDetection = st; return this; }

        /**
         * Enable streaming speech-to-text on this participant's audio; read it with
         * {@link com.synauson.jsyn.participant.Conference#streamTranscriptEvents}.
         * Needs {@link #turnDetection}, whose turn ends close each turn's transcript.
         *
         * @param stt STT configuration, or {@code null} to disable
         * @return this builder
         * @since 1.6.0
         */
        public Builder stt(@Nullable SttConfig stt) { this.stt = stt; return this; }

        /**
         * Give this participant a speaker: {@link com.synauson.jsyn.participant.Conference#speak}
         * sends text-to-speech into the call, and its playback is reported on the
         * participant's agent stream. Needs {@link #turnDetection}.
         *
         * @param tts TTS configuration, or {@code null} for no speaker
         * @return this builder
         * @since 1.6.0
         */
        public Builder tts(@Nullable TtsConfig tts) { this.tts = tts; return this; }

        /**
         * Materialise an immutable {@link WebRtcParticipantSpec}.
         *
         * @return the configured spec
         * @throws com.synauson.jsyn.exception.InvalidArgumentException naming every required
         *         field that is missing
         */
        public WebRtcParticipantSpec build() {
            Args.required("WebRtcParticipantSpec")
                .field("participantId", participantId)
                .field("sdpOffer", sdpOffer)
                .validate();
            return new WebRtcParticipantSpec(this);
        }
    }
}
