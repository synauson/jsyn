package com.synauson.jsyn.internal;

import java.nio.ByteBuffer;

/**
 * Internal: not part of the stable jsyn API. Do not use directly from application code.
 *
 * <p>Raw result returned by {@link NativeBridge#addNativeParticipant}.
 *
 * <p>The constructor signature MUST match the cached signature in the Rust JNI layer's
 * {@code jni_cache::NativeParticipantResultClass}:
 * {@code (J Ljava/nio/ByteBuffer; Ljava/nio/ByteBuffer; I)V}.
 *
 * <p>Both {@link #ingressRing} and {@link #egressRing} are direct {@link ByteBuffer}s
 * whose memory is owned by the native participant's ring buffer. They MUST NOT be used
 * after {@link NativeBridge#closeNativeParticipant} is called.
 *
 * @since 0.1.0
 */
public final class NativeParticipantNativeHandle {
    /** Opaque handle to the native NativeParticipant; passed to {@code closeNativeParticipant}. */
    public final long handle;

    /** Direct ByteBuffer over the ingress ring (Java to native audio data). */
    public final ByteBuffer ingressRing;

    /** Direct ByteBuffer over the egress ring (native to Java audio data). */
    public final ByteBuffer egressRing;

    /** Numeric audio format ID; resolve via {@link com.synauson.jsyn.NativeAudioFormat#fromId(int)}. */
    public final int formatId;

    /**
     * Construct a raw native participant handle. Invoked from the JNI layer.
     *
     * @param handle      opaque native participant handle
     * @param ingressRing direct ByteBuffer over the ingress ring memory
     * @param egressRing  direct ByteBuffer over the egress ring memory
     * @param formatId    stable audio format ID
     */
    public NativeParticipantNativeHandle(long handle, ByteBuffer ingressRing,
                                          ByteBuffer egressRing, int formatId) {
        this.handle = handle;
        this.ingressRing = ingressRing;
        this.egressRing = egressRing;
        this.formatId = formatId;
    }
}
