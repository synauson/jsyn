package com.synauson.jsyn.exception;

/**
 * A voice-agent event stream failed, for a reason you can act on.
 *
 * <p>Thrown by {@code Conference.streamAgentEvents} when the subscription is refused, and
 * passed to the observer's {@code onError} when a running stream ends early. Match on
 * {@link #reason()}, never on the message:
 *
 * <ul>
 *   <li>{@link #AGENT_SUBSCRIBER_LAGGED}: the observer fell 256 events behind and the
 *       stream was dropped. It got every event up to {@link #lastSeq()}; subscribe again
 *       with that seq as the cursor.</li>
 *   <li>{@link #AGENT_REPLAY_EXPIRED}: the cursor is older than the 512 events the
 *       stream keeps. Subscribe without a cursor.</li>
 *   <li>{@link #AGENT_STREAM_MISMATCH}: the cursor's stream id is not the participant's
 *       current stream (it was removed and added again). Subscribe without a cursor.</li>
 *   <li>{@link #TURN_DETECTION_REQUIRED}: the participant has no turn detection, so no agent
 *       stream.</li>
 * </ul>
 *
 * <p>New reasons may be added. JNI constructor:
 * {@code (Ljava/lang/String;Ljava/lang/String;J)V} (synauson-jni's
 * {@code jni_cache.rs AgentStreamClasses}).
 *
 * @since 1.6.0
 */
public class AgentStreamException extends JSynException {
    /** The subscriber fell behind and was dropped; resume after {@link #lastSeq()}. */
    public static final String AGENT_SUBSCRIBER_LAGGED = "AGENT_SUBSCRIBER_LAGGED";
    /** The cursor is older than the events the stream keeps. */
    public static final String AGENT_REPLAY_EXPIRED = "AGENT_REPLAY_EXPIRED";
    /** The cursor's stream id is not the participant's current stream. */
    public static final String AGENT_STREAM_MISMATCH = "AGENT_STREAM_MISMATCH";
    /** The participant has no turn detection, so no agent stream. */
    public static final String TURN_DETECTION_REQUIRED = "TURN_DETECTION_REQUIRED";

    private final String reason;
    private final long lastSeq;

    /**
     * Construct the exception. Invoked from the JNI layer.
     *
     * @param reason  the stable, upper-case reason
     * @param message a human-readable description
     * @param lastSeq the last seq the stream delivered, or -1 when it doesn't apply
     */
    public AgentStreamException(String reason, String message, long lastSeq) {
        super(reason + ": " + message);
        this.reason = reason;
        this.lastSeq = lastSeq;
    }

    /**
     * The reason, one of the constants on this class or a newer one.
     *
     * @return the reason
     */
    public String reason() { return reason; }

    /**
     * For {@link #AGENT_SUBSCRIBER_LAGGED}, the seq of the last event delivered: resume
     * after it. -1 for the other reasons.
     *
     * @return the last seq delivered, or -1
     */
    public long lastSeq() { return lastSeq; }
}
