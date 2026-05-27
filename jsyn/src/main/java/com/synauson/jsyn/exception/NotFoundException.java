package com.synauson.jsyn.exception;

/**
 * Thrown when a conference or participant ID does not exist.
 *
 * <p>Maps to the Rust {@code CoreError::NotFound} variant. Typically raised by
 * participant-scoped operations (mute, sendDtmf, stats, subscribe*) and by conference
 * teardown when the conference has already been removed.
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
