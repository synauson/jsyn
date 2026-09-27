package com.synauson.jsyn.exception;

/**
 * Thrown when the runtime requires authentication and none was provided.
 *
 * <p>Maps to the Rust {@code CoreError::Unauthenticated} variant. Only raised in
 * deployment modes where the runtime is started with an auth interceptor (e.g.
 * {@code --auth=token} or {@code --auth=mtls}). In the in-process jsyn embedding,
 * this exception is not expected.
 *
 * @since 0.1.0
 */
public class UnauthenticatedException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of the authentication failure
     */
    public UnauthenticatedException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of the authentication failure
     * @param cause the underlying cause
     */
    public UnauthenticatedException(String msg, Throwable cause) { super(msg, cause); }
}
