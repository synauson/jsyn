package com.synauson.jsyn.exception;

/**
 * Thrown when a conference, participant or file does not exist.
 *
 * <p>Maps to the Rust {@code CoreError::NotFound} variant. Typically raised by
 * participant-scoped operations (mute, routing, sendDtmf, stats, subscribe*), by a
 * {@code file:} URI whose file doesn't exist, and by calls on a conference that has
 * already ended.
 *
 * @since 0.1.0
 */
public class NotFoundException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of the missing resource (typically includes the ID looked up)
     */
    public NotFoundException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of the missing resource
     * @param cause the underlying cause
     */
    public NotFoundException(String msg, Throwable cause) { super(msg, cause); }
}
