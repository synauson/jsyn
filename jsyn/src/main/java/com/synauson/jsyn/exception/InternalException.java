package com.synauson.jsyn.exception;

/**
 * Thrown when an unexpected internal error occurs in the native runtime.
 *
 * <p>Maps to the Rust {@code CoreError::Internal} variant: a failure inside the engine,
 * such as a GStreamer pipeline failure, an ONNX session error or a JNI runtime issue,
 * never a mistake in the request. Treat as a fatal, non-recoverable error for the
 * affected conference or runtime.
 *
 * @since 0.1.0
 */
public class InternalException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg native-layer error description
     */
    public InternalException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   native-layer error description
     * @param cause the underlying cause
     */
    public InternalException(String msg, Throwable cause) { super(msg, cause); }
}
