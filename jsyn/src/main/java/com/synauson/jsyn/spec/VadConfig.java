package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;

/**
 * VAD (Voice Activity Detection) detector configuration.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code VadConfigInternal}:
 * snake_case field names ({@code threshold}, {@code min_silence_ms},
 * {@code min_speech_ms}).
 *
 * <p>The underlying detector is the VAD ONNX model; see
 * {@link #defaults()} for production-recommended values.
 *
 * @since 0.1.0
 */
public final class VadConfig {
    /** Probability threshold, above 0 and below 1, above which a frame is classified as speech. */
    public final float threshold;

    /** Minimum silence duration in milliseconds required to emit a {@code SpeechEnd} event. */
    @SerializedName("min_silence_ms")
    public final int minSilenceMs;

    /** Minimum speech duration in milliseconds required to emit a {@code SpeechStart} event. */
    @SerializedName("min_speech_ms")
    public final int minSpeechMs;

    /**
     * Construct a VAD configuration with the given parameters.
     *
     * <p>Adding a participant with values out of range throws
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     *
     * @param threshold    probability threshold, above 0 and below 1
     * @param minSilenceMs minimum silence duration before {@code SpeechEnd}, 0 to 10000 ms
     * @param minSpeechMs  minimum speech duration before {@code SpeechStart}, 0 to 10000 ms
     */
    public VadConfig(float threshold, int minSilenceMs, int minSpeechMs) {
        this.threshold = threshold;
        this.minSilenceMs = minSilenceMs;
        this.minSpeechMs = minSpeechMs;
    }

    /**
     * Returns a VAD config with sensible defaults matching the VAD model's
     * recommended parameters ({@code threshold=0.5}, {@code minSilenceMs=300},
     * {@code minSpeechMs=250}).
     *
     * @return default VAD configuration
     */
    public static VadConfig defaults() {
        return new VadConfig(0.5f, 300, 250);
    }
}
