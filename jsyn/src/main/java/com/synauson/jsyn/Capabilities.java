package com.synauson.jsyn;

import com.google.gson.Gson;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What this runtime may use right now, and how much of it is in use: the license with
 * its plan and standing, its two pools (concurrent AI sessions and concurrent
 * conferences), which AI capabilities the plan includes, and the state of each model.
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
     * What the limits count over: {@code "all instances of this license"} while the
     * runtime reports to the usage service, which shares the license's pool across
     * every instance and site ({@link #usageService}); {@code "this instance"} for an
     * air-gapped license, and from natives 1.7.0 and older.
     */
    public final String limitsScope;

    /**
     * Fraction above the purchased part of each pool (sessions and conferences alike)
     * still admitted as a burst, reported as overage (0.25 admits up to 125% of what was
     * purchased). The Free base never bursts.
     */
    public final double overdraft;

    /**
     * The license's pool of concurrent conferences: every live conference, with AI or
     * without, takes one from when it starts until it ends. Both pools apply: each
     * participant using AI in a conference also takes a {@linkplain #sessions session}.
     * A conference past the pool and its burst throws {@code LimitExceededException}
     * with {@code CONFERENCE_POOL_LIMIT_REACHED}; one this runtime has no share of the
     * pool for yet throws {@code FailedPreconditionException} with
     * {@code LICENSE_SHARE_PENDING} (retry later). {@code null} from natives 1.7.x, which
     * didn't limit conferences; natives older than the session pool reported only
     * {@link Pool#limit} and {@link Pool#inUse} here.
     *
     * <p>Before jsyn 1.7.0 this field was a {@link Usage}.
     *
     * @since 1.4.0
     */
    public final @Nullable Pool conferences;

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
     * takes one. {@code null} from natives older than the session pool. Its type is the
     * deprecated {@link Sessions} only so that code compiled against jsyn 1.6 keeps
     * working; read it as a {@link Pool}.
     *
     * @since 1.6.0
     */
    @SuppressWarnings("deprecation")
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

    /**
     * This runtime's standing with the usage service: its share of each of the license's
     * pools and the time it has left to reconnect. {@code null} from natives 1.7.0 and
     * older.
     *
     * @since 1.6.0
     */
    public final @Nullable UsageService usageService;

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
        this.usageService = null;
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
         * The license's standing: {@code "valid"}; {@code "expiring"} (within 14 days of
         * its expiry); {@code "offline"} (usage reports have not reached
         * {@code usage.synauson.com} for an hour or more; full function until 7 days
         * after the last one that did, paused while Synauson's own outage notice is
         * up); {@code "expired"} (a Free or paid license runs on the Free plan: every
         * AI feature in the Free pools ({@link Pool#base}), and no burst; an expired
         * developer key starts nothing new until Synauson renews it, and new work throws {@code PermissionDeniedException} with
         * {@code LICENSE_EXPIRED}); {@code "disconnected"} (7 days without a report
         * getting through: no new conference or participant starts until one does);
         * {@code "invalid-kept-last-valid"} (a renewal brought a license the engine
         * refused, and the last valid one stays in force); or {@code "rejected"}
         * (nothing new starts).
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
         * The license's plan: {@code "free"} (the Free pools, {@link Pool#base}, no
         * burst), {@code "paid"} (sessions or conferences bought on top
         * of the Free base, or unlimited), {@code "developer"} (a developer key: the Free
         * plan's pools for one year, for development and testing only, not for
         * production use) or {@code "unlicensed"} (no license in force: it was
         * rejected). A Free or paid license reads {@code "free"} once it expires; a
         * developer key stays {@code "developer"} once it expires, and starts nothing
         * new. Whether new work starts is {@link #state}'s answer, never the plan's. May
         * gain values.
         *
         * <p>Always present from natives 1.8.0; {@code null} only from natives too old
         * to send it. Before jsyn 1.7.0 this field held what new work may use, and
         * natives before 1.8.0 still send that here: {@code "all"} or {@code "none"}
         * (older natives {@code "detect"} or {@code "speech"}). That meaning is gone.
         *
         * @since 1.6.0
         */
        public final @Nullable String plan;
        /**
         * Whole days until the license expires, or, when {@code "offline"}, until new
         * calls stop without contact. {@code null} without an expiry, once expired, or
         * from older natives.
         *
         * @since 1.6.0
         */
        public final @Nullable Long daysRemaining;
        /**
         * What needs attention: why a renewal was refused, why the license check is
         * failing, or why there is no license.
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
     * One of the license's pools on this runtime: concurrent AI sessions
     * ({@link Capabilities#sessions}) or concurrent conferences
     * ({@link Capabilities#conferences}). A unit is a participant using AI or a live
     * conference. Running units are never stopped; a new one past the limit and its
     * burst throws {@code LimitExceededException} ({@code SESSION_LIMIT_REACHED} or
     * {@code CONFERENCE_POOL_LIMIT_REACHED}).
     *
     * @since 1.7.0
     */
    public static class Pool {
        /**
         * The license's limit: the Free {@link #base} plus the {@link #purchased}
         * units; {@code null} is unlimited.
         */
        public final @Nullable Integer limit;
        /** Units in use on this runtime. */
        public final int inUse;
        /**
         * The most admitted at once: the base plus the purchased units with their
         * {@link Capabilities#overdraft} burst (none on the base); {@code null} when
         * unlimited.
         */
        public final @Nullable Integer ceiling;
        /**
         * {@code "ok"}; {@code "near-limit"} (from 80% of the limit); {@code "overage"}
         * (above it, within the burst); or {@code "full"} (a new unit throws
         * {@code LimitExceededException}). {@code null} only in the conference counts
         * of natives older than the session pool.
         */
        public final String level;
        /** The most units at once since this runtime started. */
        public final int peak;
        /** RFC 3339; when {@link #peak} was first reached. */
        public final @Nullable String peakAt;
        /**
         * The Free base every license has in this pool, with no burst: the Free plan's
         * AI sessions, with every AI feature, or its conferences; 0 while the license
         * starts nothing new. 0 from natives 1.7.0 and older.
         *
         * @since 1.6.0
         */
        public final int base;
        /**
         * Units the license buys on top of the base; {@code null} is unlimited (or
         * natives 1.7.0 and older).
         *
         * @since 1.6.0
         */
        public final @Nullable Integer purchased;

        private Pool() {
            this.limit = null;
            this.inUse = 0;
            this.ceiling = null;
            this.level = null;
            this.peak = 0;
            this.peakAt = null;
            this.base = 0;
            this.purchased = null;
        }
    }

    /**
     * The pool of concurrent AI sessions on this runtime: a {@link Pool}.
     *
     * @since 1.6.0
     * @deprecated name {@link Pool} instead; {@link Capabilities#sessions} keeps this
     *     type only so that code compiled against jsyn 1.6 keeps working.
     */
    @Deprecated
    public static final class Sessions extends Pool {
        private Sessions() {}
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
        /** One VAD chunk (32 ms of audio); {@code null} until timed. */
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
         * Model id: {@code sentito-1} (VAD), {@code fermata-1} (turn detection),
         * {@code spartito-1} (STT) or {@code lettura-2} (TTS). Every model is listed; one the license doesn't
         * entitle (while it starts nothing new) is {@code "not-entitled"}.
         */
        public final String id;
        /**
         * The variant this machine runs ({@code cpu}, {@code int8} or {@code fp32}): the build
         * the engine picked, and its directory in the model store.
         *
         * @since 1.7.0
         */
        public final String variant;
        /** The model's version: its published release. */
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
            this.variant = null;
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
        /**
         * Whether participants with STT may turn eager end of turn on (see
         * {@link JSynConfig.Builder#sttEager}). {@code null} from natives without it.
         *
         * @since 1.6.0
         */
        public final @Nullable Boolean eager;

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
            this.eager = null;
        }
    }

    /**
     * TTS's synthesis pool, and how many utterances it synthesizes at once. The shape
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
        /** The cap on utterances being synthesized at once (once known), and how many are in use. */
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

    /**
     * The usage service ({@code usage.synauson.com}) as this runtime sees it. Every
     * instance of a license, servers and JVMs alike, reports its AI session and
     * conference counts there every 30 seconds and is leased a share of each of the
     * license's two pools. A runtime with no usable share of a limited pool yet (none
     * leased since it started, or the only share it holds was leased under the
     * license's earlier terms) starts nothing new in it: new work throws {@code FailedPreconditionException} with
     * {@code LICENSE_SHARE_PENDING} until a lease arrives, so retry later. While reports
     * can't get through, the runtime keeps admitting against the last share it held,
     * with its burst, for 7 days after the last report that did; calls in progress are
     * never stopped.
     *
     * @since 1.6.0
     */
    public static final class UsageService {
        /**
         * {@code "leased"} (a share leased within the last 10 minutes);
         * {@code "unreported"} (reports are not getting through: the last share and its
         * burst hold until {@link #graceEnds}); {@code "lapsed"} (7 days without
         * contact: no new conference or participant starts until a report gets
         * through); or {@code "air-gapped"} (an air-gapped license, arranged with
         * Synauson, which sends no reports). May gain values.
         */
        public final String contact;
        /**
         * RFC 3339; the last contact the 7 days count from: the newest report the
         * service accepted, moved forward while Synauson's outage notice is up.
         * {@code null} for an air-gapped license.
         */
        public final @Nullable String lastContact;
        /**
         * RFC 3339; when new calls stop unless a report gets through first. {@code null}
         * for an air-gapped license.
         */
        public final @Nullable String graceEnds;
        /** This runtime's share of the license's sessions, while leased; {@code null} when unlimited or not leased. */
        public final @Nullable Integer share;
        /** The most sessions this runtime may hold: its share plus the burst on its purchased part. */
        public final @Nullable Integer shareCeiling;
        /** Sessions in use across all the license's instances at the last report. */
        public final @Nullable Integer poolInUse;
        /** Instances holding a share at the last report. */
        public final @Nullable Integer instances;
        /**
         * RFC 3339; since when Synauson's signed notice says the usage service itself is
         * down: the 7 days are paused until it is cleared.
         */
        public final @Nullable String pausedSince;
        /**
         * RFC 3339; since when the usage service refuses this license (the licensing
         * server refused it): the 7 days run from no later than this.
         */
        public final @Nullable String refusedAt;
        /** Why the last report failed. */
        public final @Nullable String problem;
        /**
         * This runtime's share of the license's conferences, while leased; {@code null}
         * when unlimited or not leased (then no conference starts, unless the conference
         * pool is unlimited: {@code LICENSE_SHARE_PENDING}), and from natives older than
         * 1.8.0.
         *
         * @since 1.7.0
         */
        public final @Nullable Integer conferenceShare;
        /**
         * The most conferences this runtime may hold: its share plus the burst on its
         * purchased part (none on the Free base). {@code null} from natives older than
         * 1.8.0.
         *
         * @since 1.7.0
         */
        public final @Nullable Integer conferenceShareCeiling;
        /**
         * Conferences in use across all the license's instances at the last report.
         * {@code null} from natives older than 1.8.0.
         *
         * @since 1.7.0
         */
        public final @Nullable Integer conferencePoolInUse;

        private UsageService() {
            this.contact = null;
            this.lastContact = null;
            this.graceEnds = null;
            this.share = null;
            this.shareCeiling = null;
            this.poolInUse = null;
            this.instances = null;
            this.pausedSince = null;
            this.refusedAt = null;
            this.problem = null;
            this.conferenceShare = null;
            this.conferenceShareCeiling = null;
            this.conferencePoolInUse = null;
        }
    }
}
