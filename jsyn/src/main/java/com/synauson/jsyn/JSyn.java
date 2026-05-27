package com.synauson.jsyn;

import com.synauson.jsyn.internal.NativeBridge;
import com.synauson.jsyn.internal.NativeLoader;
import com.synauson.jsyn.internal.NativeResource;
import com.synauson.jsyn.participant.Conference;
import java.util.Objects;

/**
 * Entry point for the jsyn in-process media server.
 *
 * <p>Owns the Rust runtime handle. Calling {@link #close()} (or using try-with-resources)
 * shuts down the runtime and releases all native resources.
 *
 * <p>Typical lifecycle:
 * <pre>{@code
 * JSynConfig config = JSynConfig.builder()
 *     .modelsDir("/opt/synauson/models")
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
     * Construct and initialise a new jsyn runtime.
     *
     * <p>Loads and initialises the native libraries (idempotent — subsequent
     * calls reuse the already-loaded libraries). Blocks until GStreamer and
     * ONNX Runtime are initialised.
     *
     * @param config the runtime configuration
     * @throws UnsatisfiedLinkError if the native libraries are missing from the classpath
     * @throws com.synauson.jsyn.exception.InternalException if the GStreamer sanity check fails
     *         or the runtime cannot be initialised
     */
    public JSyn(JSynConfig config) {
        this(initAndGetHandle(config));
    }

    private JSyn(long runtimeHandle) {
        super(() -> NativeBridge.shutdownRuntime(runtimeHandle));
        this.runtimeHandle = runtimeHandle;
    }

    private static long initAndGetHandle(JSynConfig config) {
        Objects.requireNonNull(config, "config");
        NativeLoader.load();
        // Sanity-check GStreamer element registry before first use.
        String sanityError = NativeBridge.gstreamerSanityCheck();
        if (sanityError != null) {
            throw new com.synauson.jsyn.exception.InternalException(
                "GStreamer sanity check failed: " + sanityError);
        }
        return NativeBridge.initRuntime(NativeLoader.ortDylibAbsolutePath(), config.toJson());
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
     * @throws com.synauson.jsyn.exception.AlreadyExistsException if a conference
     *         with this ID already exists
     * @throws com.synauson.jsyn.exception.LimitExceededException if the
     *         {@code maxConferences} cap is reached
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this
     *         runtime has been closed
     */
    public Conference startConference(String conferenceId) {
        requireOpen();
        Objects.requireNonNull(conferenceId, "conferenceId");
        NativeBridge.startConference(runtimeHandle, conferenceId);
        return new Conference(runtimeHandle, conferenceId);
    }

    /**
     * Initiate graceful shutdown of all conferences in this runtime.
     *
     * <p>Equivalent to {@link #close()} with a grace period determined by the
     * runtime configuration.
     *
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     */
    public void shutdownServer() {
        requireOpen();
        NativeBridge.shutdownServer(runtimeHandle);
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
}
