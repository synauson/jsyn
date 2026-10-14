package com.synauson.jsyn.exception;

/**
 * Thrown when an operation is attempted in an invalid state, or before the runtime is
 * ready for it.
 *
 * <p>Maps to gRPC {@code FAILED_PRECONDITION} and {@code UNAVAILABLE}. The message leads
 * with the reason. Examples include adding a participant to a terminated conference,
 * sending DTMF on a non-SIP participant, or muting a participant that has already been
 * removed. Some are worth retrying later; tell them apart by {@link #reason()}:
 * <ul>
 *   <li>{@code MODEL_LOADING}: a model this participant needs is still loading.</li>
 *   <li>{@code LICENSE_SHARE_PENDING}: this runtime holds no usable share of one of the
 *       license's pools yet, as on a first start before the first usage report gets
 *       through. The pool isn't full; nothing from it starts until
 *       {@code usage.synauson.com} leases this runtime a share.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class FailedPreconditionException extends JSynException {
    /** A model this participant needs, or the STT or TTS pool, is still loading: retry later. */
    public static final String MODEL_LOADING = "MODEL_LOADING";
    /** No usable share of one of the license's pools yet: retry later. */
    public static final String LICENSE_SHARE_PENDING = "LICENSE_SHARE_PENDING";

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
