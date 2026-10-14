package com.synauson.jsyn;

import com.synauson.jsyn.internal.Args;
import com.synauson.jsyn.internal.NativeBridge;
import com.synauson.jsyn.internal.NativeLoader;
import com.synauson.jsyn.internal.NativeResource;
import com.synauson.jsyn.participant.Conference;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for the jsyn in-process media server.
 *
 * <p>Owns the Rust runtime handle. Calling {@link #close()} (or using try-with-resources)
 * shuts down the runtime and releases all native resources. Closing a runtime while its
 * speech models are still loading stops the loads and waits for any step still running,
 * which can take a few seconds (longer the first time a model is prepared).
 *
 * <p>Typical lifecycle:
 * <pre>{@code
 * JSynConfig config = JSynConfig.builder()
 *     .modelStore("/opt/synauson/models")
 *     .build();
 *
 * try (JSyn jsyn = new JSyn(config)) {
 *     try (Conference conf = jsyn.startConference("call-12345")) {
 *         // ... add participants, stream events ...
 *     }
 * }
 * }</pre>
 *
 * <p>At most one {@code JSyn} instance should be active per JVM process (the underlying
 * GStreamer and ONNX Runtime libraries are process-global singletons).
 *
 * @since 0.1.0
 */
public final class JSyn extends NativeResource {
    private final long runtimeHandle;

    /**
     * Construct and initialize a new jsyn runtime.
     *
     * <p>Loads and initializes the native libraries (idempotent: subsequent
     * calls reuse the already-loaded libraries). Blocks until GStreamer and
     * ONNX Runtime are initialized.
     *
     * @param config the runtime configuration
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code config} is null,
     *         or the license key is missing or malformed
     * @throws com.synauson.jsyn.exception.PermissionDeniedException if the licensing server
     *         refuses the key, or there is no license file that still works
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if the license file
     *         doesn't fit this engine ({@code ILLEGAL_LICENSE})
     * @throws UnsatisfiedLinkError if the native libraries are missing from the classpath
     * @throws com.synauson.jsyn.exception.InternalException if the GStreamer sanity check fails
     *         or the runtime cannot be initialized
     */
    public JSyn(JSynConfig config) {
        this(initAndGetHandle(config));
    }

    private JSyn(long runtimeHandle) {
        super(() -> NativeBridge.shutdownRuntime(runtimeHandle));
        this.runtimeHandle = runtimeHandle;
    }

    private static long initAndGetHandle(JSynConfig config) {
        Args.notNull(config, "config");
        NativeLoader.load();
        // Sanity-check GStreamer element registry before first use.
        String sanityError = NativeBridge.gstreamerSanityCheck();
        if (sanityError != null) {
            throw new com.synauson.jsyn.exception.InternalException(
                "INTERNAL: GStreamer sanity check failed: " + sanityError);
        }
        return NativeBridge.initRuntime(NativeLoader.ortDylibAbsolutePath(), config.toJson());
    }

    // -------------------------------------------------------------------------
    // Models
    // -------------------------------------------------------------------------

    /**
     * Fill a model store from a flat folder of model files, such as
     * {@code sentito-1.onnx} and {@code sentito-1-NOTICE.txt}.
     *
     * <p>Each model the natives release pins is copied into the store when all of its
     * files are in {@code from}, after its size and SHA-256 are verified. Models
     * already in the store are left alone, and files land atomically, so it is safe
     * to call on every start. Blocks while hashing and copying. Needs no {@code JSyn}
     * instance.
     *
     * @param from       folder holding the model files
     * @param modelStore store to fill, or {@code null} for the default store (see
     *                   {@link JSynConfig#modelStore})
     * @return ids of the models now installed from {@code from}, such as
     *         {@code "sentito-1"}, in catalog order (VAD, turn detection, STT, TTS)
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code from} is
     *         null or holds no model files
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if a model file in
     *         {@code from} fails verification (the store is left unchanged for it)
     * @throws com.synauson.jsyn.exception.InternalException if the store can't be written
     * @since 1.3.0
     */
    public static List<String> importModels(Path from, @Nullable Path modelStore) {
        Args.notNull(from, "from");
        NativeLoader.load();
        Map<String, @Nullable String> request = new LinkedHashMap<>();
        request.put("from", from.toAbsolutePath().toString());
        request.put("modelStore",
            modelStore == null ? null : modelStore.toAbsolutePath().toString());
        String json = NativeBridge.importModels(new GsonBuilder().serializeNulls().create()
            .toJson(request));
        ImportResult result = new Gson().fromJson(json, ImportResult.class);
        return Collections.unmodifiableList(result.imported);
    }

    /** JSON shape returned by {@code NativeBridge.importModels}. */
    private static final class ImportResult {
        List<String> imported = new ArrayList<>();
    }

    /**
     * The license notice of each model in a model store: what the model derives from
     * and under which license. Every model ships its notice in its download, and
     * redistributing a model means passing its notice on.
     *
     * <p>Each notice is checked against the size and SHA-256 the natives release pins
     * before it is returned. Models whose notice isn't in the store are left out.
     * Blocks while hashing. Needs no {@code JSyn} instance.
     *
     * @param modelStore store to read, or {@code null} for the default store (see
     *                   {@link JSynConfig#modelStore})
     * @return notice text by model id, such as {@code "lettura-2"}, in catalog order
     *         (VAD, turn detection, STT, TTS)
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if a notice in the
     *         store fails verification
     * @since 1.6.0
     */
    public static Map<String, String> modelNotices(@Nullable Path modelStore) {
        NativeLoader.load();
        Map<String, @Nullable String> request = new LinkedHashMap<>();
        request.put("modelStore",
            modelStore == null ? null : modelStore.toAbsolutePath().toString());
        request.put("model", null);
        String json = NativeBridge.modelNotices(new GsonBuilder().serializeNulls().create()
            .toJson(request));
        Map<String, String> notices = new Gson().fromJson(json,
            new com.google.gson.reflect.TypeToken<LinkedHashMap<String, String>>() { }.getType());
        return Collections.unmodifiableMap(notices);
    }

    // -------------------------------------------------------------------------
    // Conference lifecycle
    // -------------------------------------------------------------------------

    /**
     * Start a new conference with the given ID.
     *
     * <p>The returned {@link Conference} holds a reference to this runtime.
     * Close the conference before closing the {@code JSyn} instance.
     *
     * @param conferenceId the unique conference identifier
     * @return a conference handle
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code conferenceId}
     *         is null
     * @throws com.synauson.jsyn.exception.AlreadyExistsException if a conference
     *         with this ID already exists
     * @throws com.synauson.jsyn.exception.LimitExceededException if the
     *         {@code maxConferences} cap is reached ({@code CONFERENCE_LIMIT_REACHED}), or
     *         the license's concurrent conferences are in use
     *         ({@code CONFERENCE_POOL_LIMIT_REACHED})
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if this runtime
     *         holds no share of the license's conference pool yet
     *         ({@code LICENSE_SHARE_PENDING}); retry later
     * @throws com.synauson.jsyn.exception.PermissionDeniedException if the license
     *         starts nothing new ({@code LICENSE_REJECTED}, {@code LICENSE_DISCONNECTED},
     *         {@code LICENSE_EXPIRED})
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this
     *         runtime has been closed
     */
    public Conference startConference(String conferenceId) {
        requireOpen();
        Args.notNull(conferenceId, "conferenceId");
        NativeBridge.startConference(runtimeHandle, conferenceId);
        return new Conference(runtimeHandle, conferenceId);
    }

    /**
     * Terminate every conference in this runtime, giving them five seconds. Same as
     * {@link #shutdownServer(Duration)} with five seconds.
     *
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     */
    public void shutdownServer() {
        shutdownServer(Duration.ofSeconds(5));
    }

    /**
     * Terminate every conference in this runtime. Returns at once; the conferences end in
     * the background within {@code grace}. Conference handles still open refuse further
     * calls with {@link com.synauson.jsyn.exception.NotFoundException}, and new
     * conferences can still be started. Close the runtime with {@link #close()} when done.
     *
     * @param grace how long the conferences may take to terminate
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code grace} is null
     *         or negative
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     * @since 1.6.0
     */
    public void shutdownServer(Duration grace) {
        requireOpen();
        Args.notNull(grace, "grace");
        if (grace.isNegative()) {
            throw new com.synauson.jsyn.exception.InvalidArgumentException(
                "INVALID_ARGUMENT: grace must not be negative");
        }
        NativeBridge.shutdownServer(runtimeHandle, grace.toMillis());
    }

    /**
     * Retrieve a point-in-time resource snapshot (memory, conferences, CPU).
     *
     * @return a resource snapshot
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     */
    public ResourceSnapshot getResourceSnapshot() {
        requireOpen();
        String json = NativeBridge.getResourceSnapshot(runtimeHandle);
        return ResourceSnapshot.fromJson(json);
    }

    /**
     * What this runtime may use right now: the license and its state, its two pools (AI
     * sessions and conferences) and their current use, the AI capabilities it includes, and each model's state.
     *
     * @return the capabilities report
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     * @since 1.4.0
     */
    public Capabilities capabilities() {
        requireOpen();
        return Capabilities.fromJson(NativeBridge.capabilities(runtimeHandle));
    }
}
