package com.synauson.jsyn.event;

/**
 * Streaming speech-to-text event.
 *
 * <p>Emitted on the stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamTranscriptEvents} for a
 * participant added with an {@link com.synauson.jsyn.spec.SttConfig}. Closed hierarchy:
 * {@link Delta} carries committed text as it is decoded, and {@link Turn} one turn's
 * text once turn detection completes the turn.
 *
 * <p>Times are audio time: milliseconds of the participant's audio since transcription
 * started on it. Silence the transcription skipped still counts, so times line up with
 * the call.
 *
 * <p>Constructor signatures match the JNI cache in
 * {@code synauson-jni/src/jni_cache.rs TranscriptEventClasses}:
 * <ul>
 *   <li>{@code Delta}:
 *       {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JJJJ)V}</li>
 *   <li>{@code Turn}:
 *       {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JJJFZ)V}</li>
 * </ul>
 *
 * @since 1.6.0
 */
public abstract class TranscriptEvent {
    /** Conference ID this event was emitted for. */
    public final String conferenceId;
    /** Participant ID this event was emitted for. */
    public final String participantId;

    TranscriptEvent(String conferenceId, String participantId) {
        this.conferenceId = conferenceId;
        this.participantId = participantId;
    }

    /**
     * Newly committed text, never revised: a participant's deltas concatenate to its
     * transcript.
     *
     * <p>JNI constructor:
     * {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JJJJ)V}.
     *
     * @since 1.6.0
     */
    public static final class Delta extends TranscriptEvent {
        /** The new text; word-initial pieces start with a space. Empty when only {@link #tentative} changed. */
        public final String text;
        /**
         * The model's current guess at what follows, replacing any earlier one. Always
         * empty from models that don't revise their text.
         */
        public final String tentative;
        /** When the first piece of {@link #text} starts, in ms of audio time. */
        public final long startMs;
        /** When the last piece of {@link #text} starts, in ms of audio time. */
        public final long endMs;
        /** Audio transcribed so far, in ms. */
        public final long decodedMs;
        /** Audio waiting to be transcribed, in ms: how far the transcript runs behind. */
        public final long backlogMs;

        /**
         * Construct a {@code Delta} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param text          newly committed text
         * @param tentative     current tentative tail
         * @param startMs       start of the first piece
         * @param endMs         start of the last piece
         * @param decodedMs     audio transcribed so far
         * @param backlogMs     audio waiting to be transcribed
         */
        public Delta(String conferenceId, String participantId, String text, String tentative,
                     long startMs, long endMs, long decodedMs, long backlogMs) {
            super(conferenceId, participantId);
            this.text = text;
            this.tentative = tentative;
            this.startMs = startMs;
            this.endMs = endMs;
            this.decodedMs = decodedMs;
            this.backlogMs = backlogMs;
        }
    }

    /**
     * One turn's text, sent after the deltas it covers.
     *
     * <p>When turn detection reports a complete turn, the engine waits up to
     * {@link com.synauson.jsyn.spec.SttConfig#turnDrainMs} for the transcription to
     * reach the turn's end, then sends the committed text up to it that no earlier turn
     * holds. The turns' texts concatenate to the deltas' texts: each piece is in exactly
     * one turn, in order. A turn that runs out of time has {@link #complete} false, and
     * its late words open the next turn.
     *
     * <p>JNI constructor:
     * {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JJJFZ)V}.
     *
     * @since 1.6.0
     */
    public static final class Turn extends TranscriptEvent {
        /** The turn's text. */
        public final String text;
        /** When the turn's first piece starts, in ms of audio time; 0 when {@link #text} is empty. */
        public final long startMs;
        /** When the turn's last piece starts, in ms of audio time; 0 when {@link #text} is empty. */
        public final long endMs;
        /** Where turn detection said the turn ended, in ms of audio time. */
        public final long turnEndMs;
        /** Turn detection's probability for the decision. */
        public final float probability;
        /**
         * {@code false} when the transcription hadn't reached the turn's end in time (or
         * the participant left): its last words open the next turn instead.
         */
        public final boolean complete;

        /**
         * Construct a {@code Turn} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param text          the turn's text
         * @param startMs       start of the first piece
         * @param endMs         start of the last piece
         * @param turnEndMs     where the turn ended
         * @param probability   turn detection's probability
         * @param complete      whether the transcription reached the turn's end in time
         */
        public Turn(String conferenceId, String participantId, String text, long startMs,
                    long endMs, long turnEndMs, float probability, boolean complete) {
            super(conferenceId, participantId);
            this.text = text;
            this.startMs = startMs;
            this.endMs = endMs;
            this.turnEndMs = turnEndMs;
            this.probability = probability;
            this.complete = complete;
        }
    }
}
