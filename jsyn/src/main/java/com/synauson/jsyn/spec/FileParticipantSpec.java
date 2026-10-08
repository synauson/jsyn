package com.synauson.jsyn.spec;

import com.synauson.jsyn.internal.Args;
import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * Spec for adding a file participant.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code AddFileParticipantSpec}:
 * snake_case field names. The {@code participant_id} field inside the JSON is the
 * canonical identifier; it is also passed as a separate JNI argument to the native call.
 *
 * @since 0.1.0
 */
public final class FileParticipantSpec {
    /** Participant identifier; serialized as {@code participant_id}. */
    @SerializedName("participant_id")
    public final String id;

    /** GStreamer-compatible URI of the source file (e.g. {@code file:///path.wav}). */
    public final String uri;

    /** Whether to loop playback indefinitely; default {@code false}. */
    @SerializedName("loop_playback")
    public final boolean loopPlayback;

    /** Optional VAD configuration; {@code null} disables VAD detection. */
    public final @Nullable VadConfig vad;

    /**
     * Optional TurnDetection configuration; {@code null} disables TurnDetection detection. Needs
     * {@link #vad} on the same participant, whose speech ends it decides on: turn detection
     * without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     */
    @SerializedName("turn_detection")
    public final @Nullable TurnDetectionConfig turnDetection;

    /**
     * Optional streaming speech-to-text; {@code null} disables it. Needs
     * {@link #turnDetection} on the same participant: STT without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     *
     * @since 1.6.0
     */
    public final @Nullable SttConfig stt;

    /**
     * Optional speaker (text-to-speech) for
     * {@link com.synauson.jsyn.participant.Conference#speak}; {@code null} gives the
     * participant none. Needs {@link #turnDetection} on the same participant: a speaker
     * without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}. A file participant
     * has no output, so its speaker plays only where it is routed
     * ({@link TtsConfig#speakerId}).
     *
     * @since 1.6.0
     */
    public final @Nullable TtsConfig tts;

    private FileParticipantSpec(Builder b) {
        this.id = Args.notNull(b.id, "id");
        this.uri = Args.notNull(b.uri, "uri");
        this.loopPlayback = b.loopPlayback;
        this.vad = b.vad;
        this.turnDetection = b.turnDetection;
        this.stt = b.stt;
        this.tts = b.tts;
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link FileParticipantSpec}. {@code id} and {@code uri} are required.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private @Nullable String id;
        private @Nullable String uri;
        private boolean loopPlayback;
        private @Nullable VadConfig vad;
        private @Nullable TurnDetectionConfig turnDetection;
        private @Nullable SttConfig stt;
        private @Nullable TtsConfig tts;

        /**
         * Set the participant ID. Required.
         *
         * @param id participant identifier; non-null
         * @return this builder
         */
        public Builder id(String id) { this.id = id; return this; }

        /**
         * Set the source file URI. Required.
         *
         * @param uri GStreamer-compatible URI; non-null
         * @return this builder
         */
        public Builder uri(String uri) { this.uri = uri; return this; }

        /**
         * Enable or disable infinite playback looping. Default {@code false}.
         *
         * @param loopPlayback {@code true} to loop indefinitely
         * @return this builder
         */
        public Builder loopPlayback(boolean loopPlayback) { this.loopPlayback = loopPlayback; return this; }

        /**
         * Enable VAD detection on this participant's audio stream.
         *
         * @param vad VAD configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder vad(@Nullable VadConfig vad) { this.vad = vad; return this; }

        /**
         * Enable TurnDetection detection on this participant's audio stream.
         * Needs {@link #vad}, whose speech ends it decides on.
         *
         * @param st TurnDetection configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder turnDetection(@Nullable TurnDetectionConfig st) { this.turnDetection = st; return this; }

        /**
         * Enable streaming speech-to-text on this participant's audio; read it with
         * {@link com.synauson.jsyn.participant.Conference#streamTranscriptEvents}.
         * Needs {@link #turnDetection}, whose turn ends close each turn's transcript.
         *
         * @param stt STT configuration, or {@code null} to disable
         * @return this builder
         * @since 1.6.0
         */
        public Builder stt(@Nullable SttConfig stt) { this.stt = stt; return this; }

        /**
         * Give this participant a speaker: {@link com.synauson.jsyn.participant.Conference#speak}
         * sends text-to-speech into the call, and its playback is reported on the
         * participant's agent stream. Needs {@link #turnDetection}.
         *
         * @param tts TTS configuration, or {@code null} for no speaker
         * @return this builder
         * @since 1.6.0
         */
        public Builder tts(@Nullable TtsConfig tts) { this.tts = tts; return this; }

        /**
         * Materialise an immutable {@link FileParticipantSpec}.
         *
         * @return the configured spec
         * @throws com.synauson.jsyn.exception.InvalidArgumentException naming every required
         *         field that is missing
         */
        public FileParticipantSpec build() {
            Args.required("FileParticipantSpec")
                .field("id", id)
                .field("uri", uri)
                .validate();
            return new FileParticipantSpec(this);
        }
    }
}
