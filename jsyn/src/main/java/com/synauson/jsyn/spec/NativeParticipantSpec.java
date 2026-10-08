package com.synauson.jsyn.spec;

import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.internal.Args;
import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * Spec for adding a native (in-process) participant.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code NativeParticipantSpec}:
 * snake_case field names ({@code format}, {@code vad}, {@code turn_detection}). The format
 * is serialized as the enum name (e.g. {@code "PCM_S16LE16K_MONO"}).
 *
 * <p>The native participant exchanges raw audio with the JVM via shared-memory ring
 * buffers; see {@link com.synauson.jsyn.participant.NativeParticipant}.
 *
 * @since 0.1.0
 */
public final class NativeParticipantSpec {
    /** Audio format for the native ring buffers; non-null. */
    public final NativeAudioFormat format;

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

    private NativeParticipantSpec(Builder b) {
        this.format = Args.notNull(b.format, "format");
        this.vad = b.vad;
        this.turnDetection = b.turnDetection;
        this.stt = b.stt;
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link NativeParticipantSpec}. {@code format} is required.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private @Nullable NativeAudioFormat format;
        private @Nullable VadConfig vad;
        private @Nullable TurnDetectionConfig turnDetection;
        private @Nullable SttConfig stt;

        /**
         * Set the audio format. Required.
         *
         * @param format ring buffer audio format; non-null
         * @return this builder
         */
        public Builder format(NativeAudioFormat format) { this.format = format; return this; }

        /**
         * Enable VAD detection on the participant's audio stream.
         *
         * @param vad VAD configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder vad(@Nullable VadConfig vad) { this.vad = vad; return this; }

        /**
         * Enable TurnDetection detection on the participant's audio stream.
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
         * Materialise an immutable {@link NativeParticipantSpec}.
         *
         * @return the configured spec
         * @throws com.synauson.jsyn.exception.InvalidArgumentException naming every required
         *         field that is missing
         */
        public NativeParticipantSpec build() {
            Args.required("NativeParticipantSpec")
                .field("format", format)
                .validate();
            return new NativeParticipantSpec(this);
        }
    }
}
