package com.synauson.jsyn.internal;

import com.synauson.jsyn.EventStreamObserver;

/**
 * Internal: not part of the stable jsyn API. Do not use directly from application code.
 *
 * <p>Raw JNI bridge: one static native method per Rust export in {@code synauson-jni}.
 * Application code should use the higher-level
 * {@link com.synauson.jsyn.participant.Conference} and participant APIs.
 *
 * <p>The native library is loaded by {@link NativeLoader} before any method on this
 * class is called. Calling any native method before library load will throw
 * {@link UnsatisfiedLinkError}.
 *
 * <p><b>Signature contracts:</b> every method signature is fixed by the JNI name-mangling
 * rules and must exactly match the Rust {@code #[no_mangle] pub extern "system" fn}
 * declarations in {@code synauson-jni/src/exports/}. Deviations cause silent
 * {@link UnsatisfiedLinkError} at runtime.
 *
 * @since 0.1.0
 */
public final class NativeBridge {
    private NativeBridge() {}

    // -------------------------------------------------------------------------
    // Runtime lifecycle  (synauson-jni/src/exports/runtime.rs)
    // -------------------------------------------------------------------------

    /**
     * Initialise the synauson runtime and return an opaque handle.
     *
     * @param ortDylibPath absolute path to {@code libonnxruntime.so} (or {@code onnxruntime.dll}),
     *                     as extracted by {@link NativeLoader#ortDylibAbsolutePath()}
     * @param configJson   JSON configuration produced by {@code JSynConfig.toJson()}
     * @return opaque runtime handle to pass to all other methods
     */
    public static native long   initRuntime(String ortDylibPath, String configJson);

    /**
     * Release all resources owned by the runtime handle.
     *
     * @param handle runtime handle previously returned by {@link #initRuntime}
     */
    public static native void   shutdownRuntime(long handle);

    /**
     * Verify that the required GStreamer elements are present in the registry.
     * Call after library load but before {@link #initRuntime}.
     *
     * @return {@code null} on success or an error string identifying the missing element
     */
    public static native String gstreamerSanityCheck();

    /**
     * Capture a JSON resource snapshot (memory, CPU, conferences).
     *
     * @param handle runtime handle
     * @return JSON-encoded {@code ResourceSnapshot}
     */
    public static native String getResourceSnapshot(long handle);

    // -------------------------------------------------------------------------
    // Conference lifecycle  (synauson-jni/src/exports/conference.rs)
    // -------------------------------------------------------------------------

    /**
     * Start a new conference.
     *
     * @param handle runtime handle
     * @param confId conference identifier
     */
    public static native void   startConference(long handle, String confId);

    /**
     * Terminate the named conference, releasing all participants.
     *
     * @param handle runtime handle
     * @param confId conference identifier
     */
    public static native void   terminateConference(long handle, String confId);

    /**
     * Returns a JSON-encoded {@code ConferenceState}.
     *
     * @param handle runtime handle
     * @param confId conference identifier
     * @return JSON-encoded state
     */
    public static native String getConferenceState(long handle, String confId);

    /**
     * Initiate graceful shutdown of the runtime.
     *
     * @param handle runtime handle
     */
    public static native void   shutdownServer(long handle);

    // -------------------------------------------------------------------------
    // Participant lifecycle  (synauson-jni/src/exports/participant.rs)
    //
    // NOTE: addFileParticipant, addRecordingParticipant, addSipParticipant, and
    // addWebRtcParticipant each take (handle, confId, specJson) — participant_id is
    // embedded in specJson. They do NOT take a separate pid argument.
    // -------------------------------------------------------------------------

    /**
     * Add a file participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param specJson JSON-encoded {@code AddFileParticipantSpec} (contains {@code participant_id})
     * @return the assigned participant ID echoed back
     */
    public static native String addFileParticipant(long handle, String confId, String specJson);

    /**
     * Add a recording participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param specJson JSON-encoded {@code RecordingParticipantSpec} (contains {@code participant_id})
     * @return the assigned participant ID echoed back
     */
    public static native String addRecordingParticipant(long handle, String confId, String specJson);

    /**
     * Add a SIP participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param specJson JSON-encoded {@code SipParticipantJson} (camelCase)
     * @return JSON string {@code {"participantId":"...", "localRtpPort":N}}
     */
    public static native String addSipParticipant(long handle, String confId, String specJson);

    /**
     * Add a WebRTC participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param specJson JSON-encoded {@code WebRtcParticipantSpec} (snake_case)
     * @return JSON string {@code {"participantId":"...", "sdpAnswer":"..."}}
     */
    public static native String addWebRtcParticipant(long handle, String confId, String specJson);

    /**
     * Remove a participant from the conference.
     *
     * @param handle runtime handle
     * @param confId conference identifier
     * @param pid    participant identifier
     */
    public static native void   removeParticipant(long handle, String confId, String pid);

    // -------------------------------------------------------------------------
    // Routing and control  (synauson-jni/src/exports/routing.rs)
    // -------------------------------------------------------------------------

    /**
     * Mute or unmute a participant.
     *
     * @param handle runtime handle
     * @param confId conference identifier
     * @param pid    participant identifier
     * @param muted  {@code true} to mute, {@code false} to unmute
     */
    public static native void muteParticipant(long handle, String confId, String pid, boolean muted);

    /**
     * Replace the audio routing matrix for the conference.
     *
     * @param handle     runtime handle
     * @param confId     conference identifier
     * @param matrixJson JSON-encoded {@code ConnectionMatrix}
     */
    public static native void updatePartyAudioConnections(long handle, String confId, String matrixJson);

    /**
     * Inject priority audio files into a participant's playback stream.
     *
     * @param handle    runtime handle
     * @param confId    conference identifier
     * @param pid       participant identifier
     * @param filesJson JSON-encoded {@code {"uris":["..."]}}
     */
    public static native void addPriorityAudioFiles(long handle, String confId, String pid, String filesJson);

    /**
     * Add a remote trickle-ICE candidate for a WebRTC participant.
     *
     * @param handle         runtime handle
     * @param confId         conference identifier
     * @param pid            participant identifier
     * @param candidate      SDP candidate string
     * @param sdpMLineIndex  must fit in u32; passed as {@code long} to match the JNI
     *                       {@code jlong} parameter in the Rust export
     */
    public static native void addWebRtcIceCandidate(long handle, String confId, String pid,
                                                     String candidate, long sdpMLineIndex);

    // -------------------------------------------------------------------------
    // SIP / WebRTC diagnostics  (synauson-jni/src/exports/stats.rs)
    // -------------------------------------------------------------------------

    /**
     * Send a DTMF digit to a SIP participant.
     *
     * @param handle      runtime handle
     * @param confId      conference identifier
     * @param pid         participant identifier
     * @param digitNumber 0-15 (0-9 = digits, 10 = *, 11 = #, 12-15 = A-D);
     *                    passed as {@code long} to match the JNI {@code jlong} param
     * @param durationMs  duration in milliseconds; passed as {@code long} to match
     *                    the JNI {@code jlong} param
     */
    public static native void   sendDtmf(long handle, String confId, String pid,
                                          long digitNumber, long durationMs);

    /**
     * Returns a JSON-encoded {@code SipStats} for the participant.
     *
     * @param handle runtime handle
     * @param confId conference identifier
     * @param pid    participant identifier
     * @return JSON-encoded SIP stats
     */
    public static native String getSipStats(long handle, String confId, String pid);

    /**
     * Returns a JSON-encoded {@code WebRtcStats} for the participant.
     *
     * @param handle runtime handle
     * @param confId conference identifier
     * @param pid    participant identifier
     * @return JSON-encoded WebRTC stats
     */
    public static native String getWebRtcStats(long handle, String confId, String pid);

    // -------------------------------------------------------------------------
    // Event stream subscriptions  (synauson-jni/src/exports/events.rs)
    // -------------------------------------------------------------------------

    /**
     * Subscribe to VAD events for a participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param pid      participant identifier
     * @param listener observer receiving {@code VadEvent} subtypes
     * @return opaque subscription ID; pass to {@link #unsubscribe} to cancel
     */
    public static native long subscribeVadEvents(long handle, String confId, String pid,
                                                  EventStreamObserver<?> listener);

    /**
     * Subscribe to Smart Turn events for a participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param pid      participant identifier
     * @param listener observer receiving {@code SmartTurnEvent} subtypes
     * @return opaque subscription ID; pass to {@link #unsubscribe} to cancel
     */
    public static native long subscribeSmartTurnEvents(long handle, String confId, String pid,
                                                        EventStreamObserver<?> listener);

    /**
     * Subscribe to file playback events for a participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param pid      participant identifier
     * @param listener observer receiving {@code FileEvent} subtypes
     * @return opaque subscription ID; pass to {@link #unsubscribe} to cancel
     */
    public static native long subscribeFileEvents(long handle, String confId, String pid,
                                                   EventStreamObserver<?> listener);

    /**
     * Subscribe to DTMF events from a SIP participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param pid      participant identifier
     * @param listener observer receiving {@code DtmfEvent}
     * @return opaque subscription ID; pass to {@link #unsubscribe} to cancel
     */
    public static native long subscribeDtmfEvents(long handle, String confId, String pid,
                                                   EventStreamObserver<?> listener);

    /**
     * Subscribe to trickle-ICE candidates from a WebRTC participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param pid      participant identifier
     * @param listener observer receiving {@code IceCandidateEvent}
     * @return opaque subscription ID; pass to {@link #unsubscribe} to cancel
     */
    public static native long subscribeWebRtcIceCandidates(long handle, String confId, String pid,
                                                            EventStreamObserver<?> listener);

    /**
     * Cancel a subscription previously returned by one of the {@code subscribe*} methods.
     *
     * @param subId opaque subscription ID
     */
    public static native void unsubscribe(long subId);

    // -------------------------------------------------------------------------
    // Native participant  (synauson-jni/src/exports/native_participant.rs)
    // -------------------------------------------------------------------------

    /**
     * Add a native (in-process) participant.
     *
     * @param handle   runtime handle
     * @param confId   conference identifier
     * @param pid      participant identifier
     * @param specJson JSON-encoded {@code NativeParticipantSpec}
     * @return handle struct containing the opaque participant handle, direct ByteBuffers
     *         over the ingress/egress rings, and the format ID; the caller is responsible
     *         for calling {@link #closeNativeParticipant} when done
     */
    public static native NativeParticipantNativeHandle addNativeParticipant(
        long handle, String confId, String pid, String specJson);

    /**
     * Release the native participant. The ring ByteBuffers become invalid after this call.
     *
     * @param npHandle opaque native participant handle from
     *                 {@link #addNativeParticipant}
     */
    public static native void   closeNativeParticipant(long npHandle);

    /**
     * Returns ring statistics for a native participant.
     *
     * @param npHandle opaque native participant handle
     * @return 7-element {@code long[]} containing
     *         {@code [bytesWritten, bytesRead, ingressOverruns, egressOverruns,
     *         ingressUnderruns, ingressRingUtilization, egressRingUtilization]}
     */
    public static native long[] getNativeParticipantStats(long npHandle);
}
