package com.synauson.jsyn.event;

/**
 * Smart Turn Detection event.
 *
 * <p>Emitted on the Smart Turn event stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamSmartTurnEvents}.
 * Closed hierarchy: only {@link TurnResult} is a valid subtype. The
 * package-private constructor prevents external subclassing.
 *
 * <p>Constructor signatures match the JNI cache in
 * {@code synauson-jni/src/jni_cache.rs SmartTurnEventClasses}:
 * <ul>
 *   <li>{@code TurnResult}: {@code (Ljava/lang/String;Ljava/lang/String;FZ)V}</li>
 * </ul>
 *
 * @since 0.1.0
 */
public abstract class SmartTurnEvent {
    /** Conference ID this event was emitted for. */
    public final String conferenceId;
    /** Participant ID this event was emitted for. */
    public final String participantId;

    SmartTurnEvent(String conferenceId, String participantId) {
        this.conferenceId = conferenceId;
        this.participantId = participantId;
    }

    /**
     * Emitted per audio frame with the model's current turn-taking prediction.
     *
     * <p>JNI constructor: {@code (Ljava/lang/String;Ljava/lang/String;FZ)V}.
     *
     * @since 0.1.0
     */
    public static final class TurnResult extends SmartTurnEvent {
        /** Model probability that the speaker has completed their turn, in {@code [0.0, 1.0]}. */
        public final float probability;
        /** {@code true} when {@link #probability} exceeded the configured confidence threshold. */
        public final boolean turnComplete;

        /**
         * Construct a {@code TurnResult} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param probability   model probability in {@code [0.0, 1.0]}
         * @param turnComplete  whether the confidence threshold was crossed
         */
        public TurnResult(String conferenceId, String participantId,
                          float probability, boolean turnComplete) {
            super(conferenceId, participantId);
            this.probability = probability;
            this.turnComplete = turnComplete;
        }
    }
}
