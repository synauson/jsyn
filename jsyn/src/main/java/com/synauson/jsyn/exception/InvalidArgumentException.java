package com.synauson.jsyn.exception;

/**
 * Thrown when a method argument is invalid.
 *
 * <p>Maps to the Rust {@code CoreError::InvalidArgument} variant. Examples include a
 * malformed JSON spec, an unknown codec name, an empty participant ID, or an out-of-range
 * DTMF digit number. Indicates a programming error rather than a transient runtime
 * condition.
 *
 * @since 0.1.0
 */
public class InvalidArgumentException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of which argument was invalid and why
     */
    public InvalidArgumentException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of which argument was invalid and why
     * @param cause the underlying cause
     */
    public InvalidArgumentException(String msg, Throwable cause) { super(msg, cause); }
}
