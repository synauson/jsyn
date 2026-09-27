package com.synauson.jsyn.spec;

import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.internal.Args;
import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * Spec for adding a native (in-process) participant.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code NativeParticipantSpec}:
 * snake_case field names ({@code format}, {@code vad}, {@code smart_turn}). The format
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

    /** Optional SmartTurn configuration; {@code null} disables SmartTurn detection. */
    @SerializedName("smart_turn")
    public final @Nullable SmartTurnConfig smartTurn;

    private NativeParticipantSpec(Builder b) {
        this.format = Args.notNull(b.format, "format");
        this.vad = b.vad;
        this.smartTurn = b.smartTurn;
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
        private @Nullable SmartTurnConfig smartTurn;

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
         * Enable SmartTurn detection on the participant's audio stream.
         *
         * @param st SmartTurn configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder smartTurn(@Nullable SmartTurnConfig st) { this.smartTurn = st; return this; }

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
