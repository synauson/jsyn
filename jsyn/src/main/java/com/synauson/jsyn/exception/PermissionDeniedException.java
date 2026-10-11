package com.synauson.jsyn.exception;

/**
 * Thrown when the license does not cover what was requested: an AI capability it doesn't
 * include (the message names the entitlement code, e.g. {@code FEATURE_TURN_DETECTION}),
 * or anything new while the licensing server has rejected the license.
 *
 * <p>Maps to the Rust {@code CoreError::PermissionDenied} variant (gRPC
 * {@code PERMISSION_DENIED}). {@link com.synauson.jsyn.JSyn#capabilities()} shows what the license includes.
 *
 * @since 1.3.0
 */
public class PermissionDeniedException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of what is not permitted
     */
    public PermissionDeniedException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of what is not permitted
     * @param cause the underlying cause
     */
    public PermissionDeniedException(String msg, Throwable cause) { super(msg, cause); }
}
