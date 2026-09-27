package com.synauson.jsyn.exception;

/**
 * Thrown when an operation is attempted in an invalid state.
 *
 * <p>Maps to the Rust {@code CoreError::FailedPrecondition} variant. Examples include
 * adding a participant to a terminated conference, sending DTMF on a non-SIP participant,
 * or muting a participant that has already been removed.
 *
 * @since 0.1.0
 */
public class FailedPreconditionException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of the precondition that was not met
     */
    public FailedPreconditionException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of the precondition that was not met
     * @param cause the underlying cause
     */
    public FailedPreconditionException(String msg, Throwable cause) { super(msg, cause); }
}
