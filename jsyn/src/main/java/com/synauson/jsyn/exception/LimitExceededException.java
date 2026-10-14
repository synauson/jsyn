package com.synauson.jsyn.exception;

/**
 * Thrown when a limit is reached: the operator's own caps on this runtime, or one of the
 * license's pools.
 *
 * <p>Maps to gRPC {@code RESOURCE_EXHAUSTED}. The message leads with the reason:
 * <ul>
 *   <li>{@code CONFERENCE_LIMIT_REACHED}: the runtime's
 *       {@link com.synauson.jsyn.JSynConfig.Builder#maxConferences maxConferences} cap,
 *       the operator's own.</li>
 *   <li>{@code CONFERENCE_POOL_LIMIT_REACHED}: the license's concurrent conferences, with
 *       its burst, are in use (every live conference counts, with AI or without).</li>
 *   <li>{@code PARTICIPANT_LIMIT_REACHED}: the {@code maxParticipantsPerConference} cap.</li>
 *   <li>{@code SESSION_LIMIT_REACHED}: the license's concurrent AI sessions, with its
 *       burst, are in use.</li>
 *   <li>{@code RTP_PORTS_EXHAUSTED}, {@code STT_CAPACITY_REACHED}: this machine's SIP
 *       ports or speech to text capacity.</li>
 * </ul>
 * A runtime that holds no share of a license pool yet throws
 * {@link FailedPreconditionException} with {@code LICENSE_SHARE_PENDING} instead: the pool
 * isn't full. Running conferences and participants are never stopped. Callers should either back off
 * and retry later, or surface the failure to the user;
 * {@link com.synauson.jsyn.JSyn#capabilities()} shows both license pools.
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
