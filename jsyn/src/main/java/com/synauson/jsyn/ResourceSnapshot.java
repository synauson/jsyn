package com.synauson.jsyn;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Immutable point-in-time resource snapshot of the runtime.
 *
 * <p>Deserialized from the JSON returned by {@code NativeBridge.getResourceSnapshot}.
 * JSON field names match the Rust {@code ResourceSnapshot} serde output (snake_case).
 *
 * <p>Use for operational dashboards and capacity monitoring. The snapshot is captured
 * synchronously; nested counters are sampled at the same instant.
 *
 * @since 0.1.0
 */
public final class ResourceSnapshot {
    /** Unix epoch milliseconds when the snapshot was captured. */
    @SerializedName("captured_at_unix_ms")
    public final long capturedAtUnixMs;

    /** Process-level memory and thread statistics. */
    @SerializedName("process_memory")
    public final ProcessMemory processMemory;

    /** Tokio runtime worker thread counts. */
    @SerializedName("runtime_stats")
    public final RuntimeStats runtimeStats;

    /** cgroup memory limits and usage; may report zeros on non-Linux hosts. */
    @SerializedName("cgroup_memory")
    public final CgroupMemory cgroupMemory;

    /** Summary of every active conference in the runtime. */
    public final List<ConferenceInfo> conferences;

    private ResourceSnapshot() {
        this.capturedAtUnixMs = 0;
        this.processMemory = null;
        this.runtimeStats = null;
        this.cgroupMemory = null;
        this.conferences = null;
    }

    /**
     * Deserialize a resource snapshot from the JSON string returned by the native bridge.
     *
     * @param json JSON string previously returned by {@code NativeBridge.getResourceSnapshot}
     * @return the deserialised snapshot
     */
    public static ResourceSnapshot fromJson(String json) {
        return new Gson().fromJson(json, ResourceSnapshot.class);
    }

    /**
     * Process-level memory statistics sampled from {@code /proc/self/status}.
     *
     * @since 0.1.0
     */
    public static final class ProcessMemory {
        /** Resident set size in bytes (Linux {@code VmRSS}). */
        @SerializedName("vmrss_bytes")
        public final long vmrssBytes;

        /** Total live threads in the process. */
        public final int threads;

        private ProcessMemory() { this.vmrssBytes = 0; this.threads = 0; }
    }

    /**
     * Tokio runtime worker thread counts.
     *
     * @since 0.1.0
     */
    public static final class RuntimeStats {
        /** Number of standard Tokio worker threads. */
        @SerializedName("tokio_workers")
        public final int tokioWorkers;

        /** Number of Tokio blocking-pool worker threads. */
        @SerializedName("tokio_blocking_workers")
        public final int tokioBlockingWorkers;

        private RuntimeStats() { this.tokioWorkers = 0; this.tokioBlockingWorkers = 0; }
    }

    /**
     * cgroup memory limits and usage.
     *
     * <p>{@link #cgroupVersion} is {@code "v1"}, {@code "v2"}, or {@code "none"} when the
     * process is not running inside a cgroup.
     *
     * @since 0.1.0
     */
    public static final class CgroupMemory {
        /** Memory limit in bytes; {@code 0} or {@link Long#MAX_VALUE} when unlimited. */
        @SerializedName("limit_bytes")
        public final long limitBytes;

        /** Current memory usage in bytes. */
        @SerializedName("used_bytes")
        public final long usedBytes;

        /** cgroup version detected: {@code "v1"}, {@code "v2"}, or {@code "none"}. */
        @SerializedName("cgroup_version")
        public final String cgroupVersion;

        private CgroupMemory() { this.limitBytes = 0; this.usedBytes = 0; this.cgroupVersion = null; }
    }

    /**
     * Summary information for a single conference within the snapshot.
     *
     * @since 0.1.0
     */
    public static final class ConferenceInfo {
        /** Conference identifier. */
        @SerializedName("conference_id")
        public final String conferenceId;

        /** Number of participants currently in this conference. */
        @SerializedName("participant_count")
        public final int participantCount;

        private ConferenceInfo() { this.conferenceId = null; this.participantCount = 0; }
    }
}
