package com.synauson.jsyn;

import com.synauson.jsyn.internal.Args;
import com.google.gson.Gson;
import org.jspecify.annotations.Nullable;

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
    /**
     * Model store root, laid out as {@code <model-id>/<version>/<file>}.
     * {@code null} selects the default store: {@code $SYNAUSON_MODEL_STORE} if set,
     * else the per-user cache directory ({@code %LOCALAPPDATA%\synauson\models} on
     * Windows, {@code $XDG_CACHE_HOME/synauson/models} or
     * {@code ~/.cache/synauson/models} on Linux).
     *
     * @since 1.3.0
     */
    public final @Nullable String modelStore;

    /**
     * The license key from Synauson (free-tier keys included). Required: a runtime
     * without one fails to start with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}. {@code null} reads
     * {@code $SYNAUSON_LICENSE_KEY}. Never logged.
     *
     * @since 1.4.0
     */
    public final @Nullable String licenseKey;

    /**
     * A license file to use when {@code license.synauson.com} can't be reached, for
     * example one provided for an offline host. {@code null}: the runtime checks out
     * and caches its own.
     *
     * @since 1.4.0
     */
    public final @Nullable String licenseFile;

    /**
     * Directory for state that must survive restarts, such as the cached license
     * file. {@code null} selects {@code $SYNAUSON_STATE_DIR} if set, else the per-user
     * state directory ({@code %LOCALAPPDATA%\synauson\state} on Windows,
     * {@code $XDG_STATE_HOME/synauson} or {@code ~/.local/state/synauson} on Linux).
     *
     * @since 1.4.0
     */
    public final @Nullable String stateDir;

    /**
     * Never contact {@code license.synauson.com}: use {@link #licenseFile} or the cached
     * license file, else the free floor.
     *
     * @since 1.4.0
     */
    public final boolean offline;

    /** Maximum concurrent conferences. {@code null} = unlimited. */
    public final @Nullable Integer maxConferences;

    /** Maximum participants per conference. {@code null} = unlimited. */
    public final @Nullable Integer maxParticipantsPerConference;

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

    /**
     * Lowest local ICE port for WebRTC participants that set no range of their own.
     * {@code null} lets the ICE agent pick any port.
     *
     * @since 1.5.0
     */
    public final @Nullable Integer webrtcIcePortMin;

    /**
     * Highest local ICE port for WebRTC participants that set no range of their own.
     *
     * @since 1.5.0
     */
    public final @Nullable Integer webrtcIcePortMax;

    /**
     * STT decoding workers, each holding its own copy of the model. {@code null}:
     * calibrated when the runtime starts (see {@link Capabilities#stt}).
     *
     * @since 1.6.0
     */
    public final @Nullable Integer sttWorkers;

    /**
     * ONNX Runtime threads per STT worker. {@code null}: calibrated.
     *
     * @since 1.6.0
     */
    public final @Nullable Integer sttThreads;

    /**
     * Concurrent STT streams this runtime admits, under the license's own STT limit;
     * 0 turns STT off. {@code null}: what calibration finds the machine transcribes in
     * real time.
     *
     * @since 1.6.0
     */
    public final @Nullable Integer maxSttStreams;

    /**
     * Close turns early on forecasts of their last words. {@code null}: the engine
     * default, off. See {@link Builder#sttTurnFlush}.
     *
     * @since 1.6.0
     */
    public final @Nullable Boolean sttTurnFlush;

    private JSynConfig(Builder b) {
        this.modelStore = b.modelStore;
        this.licenseKey = b.licenseKey;
        this.licenseFile = b.licenseFile;
        this.stateDir = b.stateDir;
        this.offline = b.offline;
        this.maxConferences = b.maxConferences;
        this.maxParticipantsPerConference = b.maxParticipantsPerConference;
        this.rtpPortMin = b.rtpPortMin;
        this.rtpPortMax = b.rtpPortMax;
        this.rtpJitterBufferMs = b.rtpJitterBufferMs;
        this.webrtcStunServer = Args.notNull(b.webrtcStunServer, "webrtcStunServer");
        this.webrtcJitterBufferMs = b.webrtcJitterBufferMs;
        this.webrtcIcePortMin = b.webrtcIcePortMin;
        this.webrtcIcePortMax = b.webrtcIcePortMax;
        this.sttWorkers = b.sttWorkers;
        this.sttThreads = b.sttThreads;
        this.maxSttStreams = b.maxSttStreams;
        this.sttTurnFlush = b.sttTurnFlush;
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
     * <p>Default values match the production-recommended settings. Call {@link #build()}
     * to materialise the immutable configuration object.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private @Nullable String modelStore;
        private @Nullable String licenseKey;
        private @Nullable String licenseFile;
        private @Nullable String stateDir;
        private boolean offline;
        private @Nullable Integer maxConferences;
        private @Nullable Integer maxParticipantsPerConference;
        private int rtpPortMin = 10000;
        private int rtpPortMax = 20000;
        private int rtpJitterBufferMs = 200;
        private @Nullable String webrtcStunServer = "stun://stun.l.google.com:19302";
        private int webrtcJitterBufferMs = 200;
        private @Nullable Integer webrtcIcePortMin;
        private @Nullable Integer webrtcIcePortMax;
        private @Nullable Integer sttWorkers;
        private @Nullable Integer sttThreads;
        private @Nullable Integer maxSttStreams;
        private @Nullable Boolean sttTurnFlush;

        /**
         * Override the STT pool's calibrated shape and stream cap. Each {@code null}
         * keeps the calibrated value; setting all three skips calibration.
         *
         * @param workers     decoding workers (at least 1), or {@code null}
         * @param threads     ONNX Runtime threads per worker (at least 1), or {@code null}
         * @param maxStreams  concurrent STT streams admitted (0 turns STT off), or {@code null}
         * @return this builder
         * @since 1.6.0
         */
        public Builder sttCapacity(@Nullable Integer workers, @Nullable Integer threads,
                                   @Nullable Integer maxStreams) {
            this.sttWorkers = workers;
            this.sttThreads = threads;
            this.maxSttStreams = maxStreams;
            return this;
        }

        /**
         * Close each turn's transcript as soon as turn detection ends the turn. Default: off.
         *
         * <p>When on, and turn detection ends a turn whose text hasn't settled, the engine
         * decodes a forecast of the turn's last words ahead of real time and sends the
         * {@link com.synauson.jsyn.event.TranscriptEvent.Turn} on it, instead of waiting
         * for the call's own audio to get there. Measured on a Ryzen 7 3700X, it
         * closed long turns about 130 ms sooner for about 26% more CPU. Calibration sets
         * decoding aside for the forecasts, which lowers the STT stream cap by about a
         * quarter; {@link Capabilities.SttCapacity#turnFlush} and
         * {@link Capabilities.SttCapacity#forecastReserve} report it. Natives without the
         * option ignore it.
         *
         * @param on {@code true} to close turns on forecasts, {@code false} to wait for
         *        the transcription, or {@code null} for the engine default (off)
         * @return this builder
         * @since 1.6.0
         */
        public Builder sttTurnFlush(@Nullable Boolean on) {
            this.sttTurnFlush = on;
            return this;
        }

        /**
         * Set the model store the VAD and turn detection detectors load their models from.
         * Optional; see {@link JSynConfig#modelStore} for the default. Fill a store with
         * {@link JSyn#importModels}. A model missing from the store makes only the
         * features that need it fail, with a
         * {@link com.synauson.jsyn.exception.FailedPreconditionException} naming the model.
         *
         * @param modelStore absolute path to the model store root, or {@code null} for
         *        the default store
         * @return this builder
         * @since 1.3.0
         */
        public Builder modelStore(@Nullable String modelStore) {
            this.modelStore = modelStore;
            return this;
        }

        /**
         * Set the license key. Required, unless {@code $SYNAUSON_LICENSE_KEY} is set.
         * The runtime exchanges it for a signed license file at start-up and renews it
         * about daily; see {@link JSynConfig#licenseKey}.
         *
         * @param licenseKey the license key from Synauson, or {@code null} to read
         *        {@code $SYNAUSON_LICENSE_KEY}
         * @return this builder
         * @since 1.4.0
         */
        public Builder licenseKey(@Nullable String licenseKey) {
            this.licenseKey = licenseKey;
            return this;
        }

        /**
         * Set a license file to use when {@code license.synauson.com} can't be reached.
         *
         * @param licenseFile path to a license file, or {@code null}
         * @return this builder
         * @since 1.4.0
         */
        public Builder licenseFile(@Nullable String licenseFile) {
            this.licenseFile = licenseFile;
            return this;
        }

        /**
         * Set the state directory; see {@link JSynConfig#stateDir} for the default.
         *
         * @param stateDir absolute path, or {@code null} for the default
         * @return this builder
         * @since 1.4.0
         */
        public Builder stateDir(@Nullable String stateDir) {
            this.stateDir = stateDir;
            return this;
        }

        /**
         * Never contact {@code license.synauson.com}. Default: {@code false}.
         *
         * @param offline whether to stay offline
         * @return this builder
         * @since 1.4.0
         */
        public Builder offline(boolean offline) {
            this.offline = offline;
            return this;
        }

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
         * Set the default local ICE port range, {@code min} through {@code max} (1 to 65535),
         * for WebRTC participants that don't set one with
         * {@link com.synauson.jsyn.spec.WebRtcParticipantSpec.Builder#icePortRange}.
         * Default: any port. Keep it clear of the SIP range ({@code rtpPortMin} to
         * {@code rtpPortMax}); both bind UDP ports on this host.
         *
         * @param min lowest port
         * @param max highest port, at least {@code min}
         * @return this builder
         * @since 1.5.0
         */
        public Builder webrtcIcePortRange(int min, int max) {
            this.webrtcIcePortMin = min;
            this.webrtcIcePortMax = max;
            return this;
        }

        /**
         * Materialise an immutable {@link JSynConfig} from this builder's current state.
         *
         * @return the configured {@link JSynConfig} instance
         * @throws com.synauson.jsyn.exception.InvalidArgumentException naming every required
         *         field that is missing
         */
        public JSynConfig build() {
            Args.required("JSynConfig")
                .field("webrtcStunServer", webrtcStunServer)
                .validate();
            return new JSynConfig(this);
        }
    }
}
