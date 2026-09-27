package com.synauson.jsyn.event;

/**
 * Voice Activity Detection event.
 *
 * <p>Emitted on the VAD event stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamVadEvents}. Closed hierarchy:
 * only {@link SpeechStart} and {@link SpeechEnd} are valid subtypes. The
 * package-private constructor prevents external subclassing.
 *
 * <p>Constructor signatures match the JNI cache in
 * {@code synauson-jni/src/jni_cache.rs VadEventClasses}:
 * <ul>
 *   <li>{@code SpeechStart}: {@code (Ljava/lang/String;Ljava/lang/String;F)V}</li>
 *   <li>{@code SpeechEnd}:   {@code (Ljava/lang/String;Ljava/lang/String;J)V}</li>
 * </ul>
 *
 * @since 0.1.0
 */
public abstract class VadEvent {
    /** Conference ID this event was emitted for. */
    public final String conferenceId;
    /** Participant ID this event was emitted for. */
    public final String participantId;

    VadEvent(String conferenceId, String participantId) {
        this.conferenceId = conferenceId;
        this.participantId = participantId;
    }

    /**
     * Emitted when the VAD model transitions from silence to speech.
     *
     * <p>JNI constructor: {@code (Ljava/lang/String;Ljava/lang/String;F)V}.
     *
     * @since 0.1.0
     */
    public static final class SpeechStart extends VadEvent {
        /** Silero VAD confidence score in {@code [0.0, 1.0]} at the moment of detection. */
        public final float confidence;

        /**
         * Construct a {@code SpeechStart} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param confidence    Silero VAD confidence score in {@code [0.0, 1.0]}
         */
        public SpeechStart(String conferenceId, String participantId, float confidence) {
            super(conferenceId, participantId);
            this.confidence = confidence;
        }
    }

    /**
     * Emitted when the VAD model transitions from speech to silence.
     *
     * <p>JNI constructor: {@code (Ljava/lang/String;Ljava/lang/String;J)V}.
     *
     * @since 0.1.0
     */
    public static final class SpeechEnd extends VadEvent {
        /** Duration of the speech segment in milliseconds. */
        public final long durationMs;

        /**
         * Construct a {@code SpeechEnd} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param durationMs    duration of the speech segment in milliseconds
         */
        public SpeechEnd(String conferenceId, String participantId, long durationMs) {
            super(conferenceId, participantId);
            this.durationMs = durationMs;
        }
    }
}
