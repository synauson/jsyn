package com.synauson.jsyn;

import com.google.gson.Gson;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What this runtime may use right now, and how much of it is in use: the license with
 * its plan and standing, the pool of concurrent AI sessions, which AI capabilities the
 * plan includes, and the state of each model.
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

    /**
     * Fraction above the session limit still admitted as a burst, reported as overage
     * (0.25 admits up to 125%).
     */
    public final double overdraft;

    /**
     * Concurrent conferences under the license. {@code null} from natives with the
     * session pool: licenses no longer limit conferences.
     *
     * @deprecated read {@link #sessions}.
     */
    @Deprecated
    public final @Nullable Usage conferences;

    /**
     * Concurrent conferences using AI under the license. {@code null} from natives
     * with the session pool.
     *
     * @deprecated read {@link #sessions}.
     */
    @Deprecated
    public final @Nullable Usage aiConferences;

    /** Each AI capability, and whether the license's plan includes it. */
    public final List<CapabilityInfo> capabilities;

    /** Each model this runtime knows, and whether it is ready to use. */
    public final List<ModelInfo> models;

    /**
     * This machine's STT capacity. {@code null} from natives older than STT.
     *
     * @since 1.6.0
     */
    public final @Nullable SttCapacity stt;

    /**
     * The license's pool of concurrent AI sessions: each participant with any detector
     * takes one. {@code null} from natives older than the session pool.
     *
     * @since 1.6.0
     */
    public final @Nullable Sessions sessions;

    /**
     * What this runtime may use, detected at startup: its CPU budget, cores, CPU
     * features and memory limit. {@code null} from natives without it.
     *
     * @since 1.6.0
     */
    public final @Nullable Resources resources;

    /**
     * The calibration cache and the VAD and turn detection timings. STT's own timing is in
     * {@link #stt}. {@code null} from natives without the cache.
     *
     * @since 1.6.0
     */
    public final @Nullable Calibration calibration;

    /**
     * This machine's TTS capacity. {@code null} from natives older than the speaker.
     *
     * @since 1.6.0
     */
    public final @Nullable TtsCapacity tts;

    private Capabilities() {
        this.license = null;
        this.limitsScope = null;
        this.overdraft = 0;
        this.conferences = null;
        this.aiConferences = null;
        this.capabilities = null;
        this.models = null;
        this.stt = null;
        this.sessions = null;
        this.resources = null;
        this.calibration = null;
        this.tts = null;
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
         * The license's standing: {@code "valid"}; {@code "expiring"} (within 30 days of
         * its expiry); {@code "grace"} (expired, full function for 14 days);
         * {@code "expired-floor"} (past its grace: new AI work at the free floor);
         * {@code "invalid-kept-last-valid"} (a renewal brought a license the engine
         * refused, and the last valid one stays in force); {@code "free-floor"} (no
         * license file, for example while the licensing server is unreachable); or
         * {@code "rejected"}. Natives older than the session pool report
         * {@code "licensed"} or {@code "free-tier-floor"} instead of the first six.
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
        /**
         * What new work may use now: {@code "none"}, {@code "detect"} (VAD and turn
         * detection) or {@code "speech"} (adds STT and TTS; jsyn has no TTS API yet).
         * {@code null} from older natives.
         *
         * @since 1.6.0
         */
        public final @Nullable String plan;
        /**
         * Whole days until the license expires, or, in {@code "grace"}, until its grace
         * ends. {@code null} without an expiry, at a floor, or from older natives.
         *
         * @since 1.6.0
         */
        public final @Nullable Long daysRemaining;
        /**
         * What needs attention: why a renewal was refused, or why the floor applies.
         *
         * @since 1.6.0
         */
        public final @Nullable String problem;

        private LicenseInfo() {
            this.state = null;
            this.description = null;
            this.licenseId = null;
            this.name = null;
            this.source = null;
            this.fileExpiry = null;
            this.licenseExpiry = null;
            this.plan = null;
            this.daysRemaining = null;
            this.problem = null;
        }
    }

    /**
     * The pool of concurrent AI sessions on this runtime.
     *
     * @since 1.6.0
     */
    public static final class Sessions {
        /** The license's limit; {@code null} is unlimited. */
        public final @Nullable Integer limit;
        /** Sessions in use. */
        public final int inUse;
        /** The most admitted at once with the {@link #overdraft} burst; {@code null} when unlimited. */
        public final @Nullable Integer ceiling;
        /**
         * {@code "ok"}; {@code "near-limit"} (from 80% of the limit); {@code "overage"}
         * (above it, within the burst); or {@code "full"} (a new participant with a
         * detector throws {@code LimitExceededException}).
         */
        public final String level;
        /** The most sessions at once since this runtime started. */
        public final int peak;
        /** RFC 3339; when {@link #peak} was first reached. */
        public final @Nullable String peakAt;

        private Sessions() {
            this.limit = null;
            this.inUse = 0;
            this.ceiling = null;
            this.level = null;
            this.peak = 0;
            this.peakAt = null;
        }
    }

    /**
     * The resources the runtime sized its model pools by. Several runtimes on one host
     * (containers, JVMs) each size themselves to their own CPU quota or cpuset, not to
     * the host's CPU count; set {@link JSynConfig.Builder#cpuBudget} to give one a share.
     *
     * @since 1.6.0
     */
    public static final class Resources {
        /** Cores this runtime may use; fractional under a CPU quota. */
        public final double cpuBudget;
        /** {@code "auto"} (detected) or {@code "override"} ({@link JSynConfig.Builder#cpuBudget}). */
        public final String cpuSource;
        /**
         * What set the budget: {@code "cpu_max"} (a cgroup CPU quota), {@code "cpuset"},
         * {@code "affinity"}, {@code "std_parallelism"} (the CPU count) or
         * {@code "override"}.
         */
        public final String limitedBy;
        /** Whole cores the model pools size by: the budget rounded down, at least 1. */
        public final int cores;
        /** Logical CPUs the operating system reports. */
        public final int logicalCpus;
        /** Physical cores among the CPUs the runtime may use; {@code null} where unknown. */
        public final @Nullable Integer physicalCores;
        /** Performance CPUs on a hybrid part; {@code null} with one core type. */
        public final @Nullable Integer performanceCpus;
        /** The CPU's model name. */
        public final String cpuModel;
        /**
         * Instruction set extensions the inference runtime picks kernels by:
         * {@code avx2}, {@code avx512f}, {@code avx512vnni}, {@code avxvnni},
         * {@code amx_tile}, {@code amx_int8}; on ARM64 {@code dotprod}, {@code i8mm},
         * {@code bf16}.
         */
        public final List<String> cpuFlags;
        /** The memory limit in bytes (a cgroup's, or {@link JSynConfig.Builder#memoryBudget}); {@code null} with neither. */
        public final @Nullable Long memoryLimitBytes;
        /** {@code "auto"} or {@code "override"}. */
        public final String memorySource;

        private Resources() {
            this.cpuBudget = 0;
            this.cpuSource = null;
            this.limitedBy = null;
            this.cores = 0;
            this.logicalCpus = 0;
            this.physicalCores = null;
            this.performanceCpus = null;
            this.cpuModel = null;
            this.cpuFlags = null;
            this.memoryLimitBytes = null;
            this.memorySource = null;
        }
    }

    /**
     * The calibration cache: the model timings the runtime sized its pools by, kept in
     * {@code calibration.json} in the {@linkplain JSynConfig#stateDir state directory}
     * so a restart reuses them instead of timing again.
     *
     * @since 1.6.0
     */
    public static final class Calibration {
        /** The cache file, {@code <stateDir>/calibration.json}; {@code null} without a state directory. */
        public final @Nullable String file;
        /** Whether this start ignored the cached timings ({@link JSynConfig.Builder#recalibrate}). */
        public final boolean recalibrate;
        /** One sentito-1 chunk (32 ms of audio); {@code null} until timed. */
        public final @Nullable DetectorTiming vad;
        /** One turn detection decision; {@code null} until timed. */
        public final @Nullable DetectorTiming turnDetection;

        private Calibration() {
            this.file = null;
            this.recalibrate = false;
            this.vad = null;
            this.turnDetection = null;
        }
    }

    /**
     * How long one detector step takes on this machine. The engine times it in the
     * background at startup once the model is in the store, or reads it from the
     * calibration cache.
     *
     * @since 1.6.0
     */
    public static final class DetectorTiming {
        /** Model id, e.g. {@code fermata-1}. */
        public final String model;
        /** ONNX Runtime threads the step ran on. */
        public final int threads;
        /** Milliseconds one step takes. */
        public final double ms;
        /** {@code "auto"} (timed on this start) or {@code "cached"} (on an earlier one). */
        public final String source;

        private DetectorTiming() {
            this.model = null;
            this.threads = 0;
            this.ms = 0;
            this.source = null;
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
        /**
         * Entitlement code: {@code FEATURE_VAD}, {@code FEATURE_TURN_DETECTION},
         * {@code FEATURE_STT} or {@code FEATURE_TTS}, in that order. Newer natives may
         * append codes.
         */
        public final String code;
        /**
         * Whether the license's plan includes it, itself or through an entitlement that
         * includes it: {@code FEATURE_STT} and {@code FEATURE_TTS} each include turn
         * detection, which includes VAD.
         */
        public final boolean entitled;
        /**
         * Streams counted against it. {@code null} from natives with the session pool.
         *
         * @deprecated read {@link Capabilities#sessions}.
         */
        @Deprecated
        public final @Nullable Usage streams;
        /**
         * The entitlement code that grants this capability when the license doesn't name
         * it itself, e.g. {@code FEATURE_STT} for turn detection under a license naming
         * only {@code FEATURE_STT} and {@code FEATURE_TTS}. {@code null} when the license
         * names it, doesn't include it, or the natives predate inclusion.
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
        /**
         * Model id: {@code sentito-1}, {@code fermata-1}, {@code spartito-1} (STT) or
         * {@code lettura-1} (TTS). Every model is listed; one the plan lacks is
         * {@code "not-entitled"}.
         */
        public final String id;
        /** Directory name of this version in the model store. */
        public final String version;
        /** Published release version. */
        public final String release;
        /**
         * {@code "ready"}, {@code "downloading"}, {@code "missing"}, {@code "invalid"} or
         * {@code "not-entitled"}. A failed download stays {@code "missing"} or {@code "invalid"}
         * and is retried on its own; {@link #detail} says why and when.
         */
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
     * model copy takes), or set with {@link JSynConfig.Builder#sttCapacity}. Each STT
     * participant also takes a license session.
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
        /**
         * The private memory each worker's model adds, when measured (Linux). Weights
         * the workers share are counted once, in {@link #sharedModelBytes}.
         */
        public final @Nullable Long modelBytes;
        /**
         * Model weights the workers share, paid once: the pool's model memory is about
         * {@code workers * modelBytes + sharedModelBytes}. 0 when each worker loads its
         * own copy; {@code null} until measured, or from natives without sharing.
         *
         * @since 1.6.0
         */
        public final @Nullable Long sharedModelBytes;
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
        /**
         * Where the numbers came from: {@code "auto"} (timed on this start),
         * {@code "cached"} (every timing read from the calibration cache, see
         * {@link Capabilities#calibration}) or {@code "override"} (all set with
         * {@link JSynConfig.Builder#sttCapacity}). {@code null} until known, or from
         * natives without the cache.
         */
        public final @Nullable String source;

        private SttCapacity() {
            this.state = null;
            this.calibrated = null;
            this.workers = null;
            this.threadsPerWorker = null;
            this.realTimeFactor = null;
            this.modelBytes = null;
            this.sharedModelBytes = null;
            this.limitedBy = null;
            this.streams = null;
            this.detail = null;
            this.turnFlush = null;
            this.forecastReserve = null;
            this.source = null;
        }
    }

    /**
     * TTS's synthesis pool, and how many utterances it synthesises at once. The shape
     * comes from the CPU count, or is set with {@link JSynConfig.Builder#ttsCapacity}. A
     * speaker takes a place only while it speaks: from its utterance's first chunk until
     * the utterance is played or interrupted. Each participant with a speaker also takes
     * a license session.
     *
     * @since 1.6.0
     */
    public static final class TtsCapacity {
        /**
         * {@code "ready"}; {@code "loading"} (the engine loads in the background, and
         * adding a participant with a speaker throws {@code FailedPreconditionException}
         * until it is ready); {@code "idle"} (not started: no license or model yet,
         * {@link #detail} says which); or {@code "failed"} (the next participant with a
         * speaker tries again).
         */
        public final String state;
        /** Synthesis workers, once known. */
        public final @Nullable Integer workers;
        /** ONNX Runtime threads per worker, once known. */
        public final @Nullable Integer threadsPerWorker;
        /** The cap on utterances synthesising at once (once known), and how many are. */
        public final Usage streams;
        /** Why it is idle, or failed. */
        public final @Nullable String detail;

        private TtsCapacity() {
            this.state = null;
            this.workers = null;
            this.threadsPerWorker = null;
            this.streams = null;
            this.detail = null;
        }
    }
}
