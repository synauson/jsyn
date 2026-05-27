package com.synauson.jsyn;

import com.google.gson.Gson;
import java.util.Objects;

/**
 * Configuration for a {@link JSyn} runtime instance.
 *
 * <p>Constructed via the {@link Builder} fluent API and passed to the {@code JSyn}
 * constructor. Serialized to JSON via {@link #toJson()} and forwarded to the native
 * {@code NativeBridge.initRuntime(ortDylibPath, configJson)} call.
 *
 * <p>Field names are camelCase to match the Rust {@code ConfigJson}
 * ({@code serde(rename_all = "camelCase")}) used in the JNI layer. Do not change
 * field names without updating the Rust side.
 *
 * @since 0.1.0
 */
public final class JSynConfig {
    /** Absolute path to the directory containing the ONNX model files. Non-null. */
    public final String modelsDir;

    /** Maximum concurrent conferences. {@code null} = unlimited. */
    public final Integer maxConferences;

    /** Maximum participants per conference. {@code null} = unlimited. */
    public final Integer maxParticipantsPerConference;

    /** Lower bound (inclusive) of the UDP port range used for SIP RTP. */
    public final int rtpPortMin;

    /** Upper bound (inclusive) of the UDP port range used for SIP RTP. */
    public final int rtpPortMax;

    /** GStreamer rtpjitterbuffer latency in milliseconds for SIP participants. */
    public final int rtpJitterBufferMs;

    /** STUN server URI used for WebRTC ICE negotiation (e.g. {@code "stun://stun.l.google.com:19302"}). */
    public final String webrtcStunServer;

    /** GStreamer webrtcbin jitter buffer latency in milliseconds for WebRTC participants. */
    public final int webrtcJitterBufferMs;

    private JSynConfig(Builder b) {
        this.modelsDir = Objects.requireNonNull(b.modelsDir, "modelsDir");
        this.maxConferences = b.maxConferences;
        this.maxParticipantsPerConference = b.maxParticipantsPerConference;
        this.rtpPortMin = b.rtpPortMin;
        this.rtpPortMax = b.rtpPortMax;
        this.rtpJitterBufferMs = b.rtpJitterBufferMs;
        this.webrtcStunServer = Objects.requireNonNull(b.webrtcStunServer, "webrtcStunServer");
        this.webrtcJitterBufferMs = b.webrtcJitterBufferMs;
    }

    /**
     * Serialize this configuration to the JSON string expected by
     * {@code NativeBridge.initRuntime}.
     *
     * @return JSON representation suitable for passing to the native runtime
     */
    public String toJson() {
        return new Gson().toJson(this);
    }

    /**
     * Returns a new {@link Builder} for constructing a {@link JSynConfig}.
     *
     * @return a fresh builder with default values populated
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link JSynConfig}.
     *
     * <p>Default values match the production-recommended settings; only {@link #modelsDir}
     * must be supplied explicitly. Call {@link #build()} to materialise the immutable
     * configuration object.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private String modelsDir;
        private Integer maxConferences;
        private Integer maxParticipantsPerConference;
        private int rtpPortMin = 10000;
        private int rtpPortMax = 20000;
        private int rtpJitterBufferMs = 200;
        private String webrtcStunServer = "stun://stun.l.google.com:19302";
        private int webrtcJitterBufferMs = 200;

        /**
         * Set the directory containing ONNX model files (Silero VAD, Smart Turn).
         * Required; the build will fail without this.
         *
         * @param modelsDir absolute path to the model directory; non-null
         * @return this builder
         */
        public Builder modelsDir(String modelsDir) { this.modelsDir = modelsDir; return this; }

        /**
         * Cap the number of concurrent conferences. Default: unlimited.
         *
         * @param max maximum concurrent conferences; must be positive
         * @return this builder
         */
        public Builder maxConferences(int max) { this.maxConferences = max; return this; }

        /**
         * Cap the number of participants per conference. Default: unlimited.
         *
         * @param max maximum participants per conference; must be positive
         * @return this builder
         */
        public Builder maxParticipantsPerConference(int max) {
            this.maxParticipantsPerConference = max;
            return this;
        }

        /**
         * Set the lower bound (inclusive) of the UDP port range used for SIP RTP.
         * Default: {@code 10000}.
         *
         * @param port lower bound, in the range {@code [1, 65535]}
         * @return this builder
         */
        public Builder rtpPortMin(int port) { this.rtpPortMin = port; return this; }

        /**
         * Set the upper bound (inclusive) of the UDP port range used for SIP RTP.
         * Default: {@code 20000}.
         *
         * @param port upper bound, must be {@code >= rtpPortMin}
         * @return this builder
         */
        public Builder rtpPortMax(int port) { this.rtpPortMax = port; return this; }

        /**
         * Set the GStreamer {@code rtpjitterbuffer} latency for SIP participants.
         * Default: {@code 200 ms}.
         *
         * @param ms jitter buffer latency in milliseconds; non-negative
         * @return this builder
         */
        public Builder rtpJitterBufferMs(int ms) { this.rtpJitterBufferMs = ms; return this; }

        /**
         * Set the STUN server URI for WebRTC ICE. Default: Google's public STUN
         * ({@code stun://stun.l.google.com:19302}).
         *
         * @param uri STUN server URI; non-null
         * @return this builder
         */
        public Builder webrtcStunServer(String uri) { this.webrtcStunServer = uri; return this; }

        /**
         * Set the GStreamer {@code webrtcbin} jitter buffer latency for WebRTC
         * participants. Default: {@code 200 ms}.
         *
         * @param ms jitter buffer latency in milliseconds; non-negative
         * @return this builder
         */
        public Builder webrtcJitterBufferMs(int ms) { this.webrtcJitterBufferMs = ms; return this; }

        /**
         * Materialise an immutable {@link JSynConfig} from this builder's current state.
         *
         * @return the configured {@link JSynConfig} instance
         * @throws NullPointerException if {@code modelsDir} or {@code webrtcStunServer} is null
         */
        public JSynConfig build() { return new JSynConfig(this); }
    }
}
