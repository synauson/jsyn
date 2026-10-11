package com.synauson.jsyn.event;

/**
 * File participant playback event.
 *
 * <p>Emitted on the file event stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamFileEvents}. Closed hierarchy:
 * only {@link PlaybackStarted}, {@link PlaybackEnded}, {@link Eos}, and
 * {@link FileError} are valid subtypes. The package-private constructor prevents
 * external subclassing.
 *
 * <p>Constructor signatures match the JNI cache in
 * {@code synauson-jni/src/jni_cache.rs FileEventClasses}:
 * <ul>
 *   <li>{@code PlaybackStarted}: {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V}</li>
 *   <li>{@code PlaybackEnded}:   {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V}</li>
 *   <li>{@code Eos}:             {@code (Ljava/lang/String;Ljava/lang/String;)V}</li>
 *   <li>{@code FileError}:       {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V}</li>
 * </ul>
 *
 * @since 0.1.0
 */
public abstract class FileEvent {
    /** Conference ID this event was emitted for. */
    public final String conferenceId;
    /** Participant ID this event was emitted for. */
    public final String participantId;

    FileEvent(String conferenceId, String participantId) {
        this.conferenceId = conferenceId;
        this.participantId = participantId;
    }

    /**
     * Emitted when a file URI starts playing (first buffer decoded).
     *
     * <p>JNI constructor: {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V}.
     *
     * @since 0.1.0
     */
    public static final class PlaybackStarted extends FileEvent {
        /** The URI that started playing. */
        public final String uri;

        /**
         * Construct a {@code PlaybackStarted} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param uri           URI that started playing
         */
        public PlaybackStarted(String conferenceId, String participantId, String uri) {
            super(conferenceId, participantId);
            this.uri = uri;
        }
    }

    /**
     * Emitted when a file URI finishes playing (EOS received from that source).
     *
     * <p>JNI constructor: {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V}.
     *
     * @since 0.1.0
     */
    public static final class PlaybackEnded extends FileEvent {
        /** The URI that finished playing. */
        public final String uri;

        /**
         * Construct a {@code PlaybackEnded} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param uri           URI that finished playing
         */
        public PlaybackEnded(String conferenceId, String participantId, String uri) {
            super(conferenceId, participantId);
            this.uri = uri;
        }
    }

    /**
     * Emitted when the entire file participant reaches end-of-stream (all URIs played,
     * {@code loop_playback} is false or was not requested).
     *
     * <p>JNI constructor: {@code (Ljava/lang/String;Ljava/lang/String;)V}.
     *
     * @since 0.1.0
     */
    public static final class Eos extends FileEvent {
        /**
         * Construct an {@code Eos} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         */
        public Eos(String conferenceId, String participantId) {
            super(conferenceId, participantId);
        }
    }

    /**
     * Emitted when a file can't be played: no audio could be decoded from it when it was
     * added (the add throws too), or its decoder failed while it played. A priority file
     * that fails sends this and is skipped: the next one plays, or the participant's own
     * file resumes.
     *
     * <p>JNI constructor: {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V}.
     *
     * @since 0.1.0
     */
    public static final class FileError extends FileEvent {
        /** Why the file can't be played. */
        public final String message;

        /**
         * Construct a {@code FileError} event. Invoked from the JNI layer.
         *
         * @param conferenceId  conference identifier
         * @param participantId participant identifier
         * @param message       human-readable error message
         */
        public FileError(String conferenceId, String participantId, String message) {
            super(conferenceId, participantId);
            this.message = message;
        }
    }
}
