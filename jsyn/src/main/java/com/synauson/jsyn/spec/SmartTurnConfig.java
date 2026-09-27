package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;

/**
 * SmartTurn detector configuration.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code SmartTurnConfigInternal}:
 * snake_case field names ({@code buffered_samples}, {@code confidence_threshold}).
 *
 * <p>The underlying detector is the Smart Turn ONNX model; see {@link #defaults()} for
 * production-recommended values.
 *
 * @since 0.1.0
 */
public final class SmartTurnConfig {
    /** Number of audio samples buffered before running the model. */
    @SerializedName("buffered_samples")
    public final int bufferedSamples;

    /** Probability threshold in {@code [0.0, 1.0]} for emitting {@code turnComplete=true}. */
    @SerializedName("confidence_threshold")
    public final float confidenceThreshold;

    /**
     * Construct a SmartTurn configuration with the given parameters.
     *
     * @param bufferedSamples     samples to buffer before inference; must be positive
     * @param confidenceThreshold threshold in {@code [0.0, 1.0]} for turn-complete classification
     */
    public SmartTurnConfig(int bufferedSamples, float confidenceThreshold) {
        this.bufferedSamples = bufferedSamples;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Returns a SmartTurn config with sensible defaults matching the model's
     * recommended parameters ({@code bufferedSamples=160},
     * {@code confidenceThreshold=0.5}).
     *
     * @return default SmartTurn configuration
     */
    public static SmartTurnConfig defaults() {
        return new SmartTurnConfig(160, 0.5f);
    }
}
