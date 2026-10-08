package com.synauson.jsyn;

import com.google.gson.Gson;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What this runtime may use right now, and how much of it is in use: the license,
 * its usage limits and current usage, which AI capabilities it includes, and the state
 * of each model.
 *
 * <p>Deserialized from the JSON returned by {@code NativeBridge.capabilities}; field
 * names match the Rust {@code CapabilitiesReport} (camelCase).
 *
 * @since 1.4.0
 */
public final class Capabilities {
    /** The license and its state. */
    public final LicenseInfo license;

    /**
     * What the limits count over: {@code "this instance"} until usage is aggregated
     * across every instance of a license.
     */
    public final String limitsScope;

    /** Fraction above each limit still admitted, reported as overage (0.25 = 125%). */
    public final double overdraft;

    /** Concurrent conferences. */
    public final Usage conferences;

    /** Concurrent conferences with at least one AI capability in use. */
    public final Usage aiConferences;

    /** Each AI capability, whether the license includes it, and its stream usage. */
    public final List<CapabilityInfo> capabilities;

    /** Each model this runtime knows, and whether it is ready to use. */
    public final List<ModelInfo> models;

    /**
     * This machine's STT capacity. {@code null} from natives older than STT.
     *
     * @since 1.6.0
     */
    public final @Nullable SttCapacity stt;

    private Capabilities() {
        this.license = null;
        this.limitsScope = null;
        this.overdraft = 0;
        this.conferences = null;
        this.aiConferences = null;
        this.capabilities = null;
        this.models = null;
        this.stt = null;
    }

    /**
     * Deserialize from the JSON string returned by the native bridge.
     *
     * @param json JSON previously returned by {@code NativeBridge.capabilities}
     * @return the deserialized report
     */
    public static Capabilities fromJson(String json) {
        return new Gson().fromJson(json, Capabilities.class);
    }

    /**
     * The license this runtime runs under.
     *
     * @since 1.4.0
     */
    public static final class LicenseInfo {
        /**
         * {@code "licensed"}, {@code "free-tier-floor"} (no current license file, for
         * example while the licensing server is unreachable), or {@code "rejected"}.
         */
        public final String state;
        /** One line describing the license and its state. */
        public final String description;
        /** Licensing server id of the license, when licensed. */
        public final @Nullable String licenseId;
        /** Name of the license, when licensed. */
        public final @Nullable String name;
        /** Where the license file in use came from: {@code "server"}, {@code "cache"} or {@code "provided"}. */
        public final @Nullable String source;
        /** RFC 3339; when the license file in use stops being valid offline. */
        public final @Nullable String fileExpiry;
        /** RFC 3339; the license's own expiry, if it has one. */
        public final @Nullable String licenseExpiry;

        private LicenseInfo() {
            this.state = null;
            this.description = null;
            this.licenseId = null;
            this.name = null;
            this.source = null;
            this.fileExpiry = null;
            this.licenseExpiry = null;
        }
    }

    /**
     * A limit and how much of it is in use.
     *
     * @since 1.4.0
     */
    public static final class Usage {
        /** The limit; {@code null} is unlimited. */
        public final @Nullable Integer limit;
        /** Units currently in use. */
        public final int inUse;

        private Usage() {
            this.limit = null;
            this.inUse = 0;
        }
    }

    /**
     * One AI capability.
     *
     * @since 1.4.0
     */
    public static final class CapabilityInfo {
        /** Entitlement code, e.g. {@code FEATURE_TURN_DETECTION}. */
        public final String code;
        /**
         * Whether the license includes it, itself or through an entitlement that includes
         * it: {@code FEATURE_STT} includes turn detection, which includes VAD.
         */
        public final boolean entitled;
        /**
         * Concurrent streams counted against it: one per participant whose highest
         * detector it grants. The detectors below that one are included and count nothing.
         */
        public final Usage streams;
        /**
         * The entitlement code that grants this capability when the license doesn't name
         * it itself, e.g. {@code FEATURE_STT} for turn detection under a license with only
         * {@code FEATURE_STT}. Streams of this capability then count against that one.
         * {@code null} when the license names it, doesn't include it, or the natives
         * predate inclusion.
         *
         * @since 1.6.0
         */
        public final @Nullable String includedBy;

        private CapabilityInfo() {
            this.code = null;
            this.entitled = false;
            this.streams = null;
            this.includedBy = null;
        }
    }

    /**
     * One model.
     *
     * @since 1.4.0
     */
    public static final class ModelInfo {
        /** Model id, e.g. {@code sentito-1}. */
        public final String id;
        /** Directory name of this version in the model store. */
        public final String version;
        /** Published release version. */
        public final String release;
        /** {@code "ready"}, {@code "missing"}, {@code "invalid"} or {@code "not-entitled"}. */
        public final String state;
        /** Why, when not ready. */
        public final @Nullable String detail;

        private ModelInfo() {
            this.id = null;
            this.version = null;
            this.release = null;
            this.state = null;
            this.detail = null;
        }
    }

    /**
     * STT's decoding pool, and how many STT streams this machine transcribes in real
     * time: measured when the pool starts (a timed decode, and the memory one worker's
     * model copy takes), or set with {@link JSynConfig.Builder#sttCapacity}. The
     * license's {@code FEATURE_STT} stream limit applies on top.
     *
     * @since 1.6.0
     */
    public static final class SttCapacity {
        /**
         * {@code "ready"}; {@code "loading"} (the pool loads in the background, and
         * adding a participant with STT throws {@code FailedPreconditionException}
         * until it is ready); {@code "idle"} (not started: no license or model yet,
         * {@link #detail} says which); or {@code "failed"} (the next STT participant
         * tries again).
         */
        public final String state;
        /** Whether the numbers were measured ({@code true}) or set by the operator. */
        public final @Nullable Boolean calibrated;
        /** Decoding workers, once known. */
        public final @Nullable Integer workers;
        /** ONNX Runtime threads per worker, once known. */
        public final @Nullable Integer threadsPerWorker;
        /** One stream's decode time over audio time, when measured. */
        public final @Nullable Double realTimeFactor;
        /** Resident memory of one worker's model copy, when measured (Linux). */
        public final @Nullable Long modelBytes;
        /** What set the worker count: {@code "cpu"}, {@code "memory"} or {@code "operator"}. */
        public final @Nullable String limitedBy;
        /** The stream cap (once known) and the STT streams in use. */
        public final Usage streams;
        /** Why it is idle, or failed. */
        public final @Nullable String detail;
        /**
         * Whether turns close early on forecasts (see
         * {@link JSynConfig.Builder#sttTurnFlush}). {@code null} from natives without
         * the turn flush.
         */
        public final @Nullable Boolean turnFlush;
        /**
         * Decoding the stream cap sets aside per stream for the turn flush's forecasts,
         * as a share of the stream's own: 0 when the flush is off. {@code null} when the
         * capacity wasn't measured, or from natives without the turn flush.
         */
        public final @Nullable Double forecastReserve;

        private SttCapacity() {
            this.state = null;
            this.calibrated = null;
            this.workers = null;
            this.threadsPerWorker = null;
            this.realTimeFactor = null;
            this.modelBytes = null;
            this.limitedBy = null;
            this.streams = null;
            this.detail = null;
            this.turnFlush = null;
            this.forecastReserve = null;
        }
    }
}
