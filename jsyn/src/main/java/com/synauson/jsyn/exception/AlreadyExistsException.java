package com.synauson.jsyn.exception;

/**
 * Thrown when a conference or participant with the given ID already exists.
 *
 * <p>Maps to the Rust {@code CoreError::AlreadyExists} variant. Typically raised by
 * conference start, participant add, and any other create-style RPC where the chosen
 * identifier collides with a live resource.
 *
 * @since 0.1.0
 */
public class AlreadyExistsException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of the duplicate identifier (typically includes the colliding ID)
     */
    public AlreadyExistsException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of the duplicate identifier
     * @param cause the underlying cause
     */
    public AlreadyExistsException(String msg, Throwable cause) { super(msg, cause); }
}
