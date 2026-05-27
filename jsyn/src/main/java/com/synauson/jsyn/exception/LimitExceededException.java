package com.synauson.jsyn.exception;

/**
 * Thrown when a configured runtime limit is exceeded.
 *
 * <p>Maps to the Rust {@code CoreError::LimitExceeded} variant. The most common cases are
 * the {@code maxConferences} cap on a {@code JSyn} runtime and the
 * {@code maxParticipantsPerConference} cap on an individual conference. Callers should
 * either back off and retry later, or surface the failure to the user.
 *
 * @since 0.1.0
 */
public class LimitExceededException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of which limit was exceeded and its configured value
     */
    public LimitExceededException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of which limit was exceeded
     * @param cause the underlying cause
     */
    public LimitExceededException(String msg, Throwable cause) { super(msg, cause); }
}
