package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * Turn-detection configuration.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code TurnDetectionConfigInternal}:
 * snake_case field names ({@code buffered_samples}, {@code confidence_threshold}).
 *
 * <p>The underlying detector is the turn-detection ONNX model; see {@link #defaults()} for
 * production-recommended values. It decides when VAD reports a speech end, so it needs a
 * {@link VadConfig} on the same participant; without one, adding the participant throws
 * {@link com.synauson.jsyn.exception.InvalidArgumentException}. Turn detection also gives the
 * participant a voice-agent event stream, whose turn config starts from {@link #turns}
 * ({@link #withTurns}).
 *
 * @since 0.1.0
 */
public final class TurnDetectionConfig {
    /**
     * Audio (16 kHz samples) the participant must have sent before turn detection decides:
     * below the model's minimum of 4000 (250 ms) means that minimum, at most 128000.
     */
    @SerializedName("buffered_samples")
    public final int bufferedSamples;

    /** Probability threshold in {@code [0.0, 1.0]} for emitting {@code turnComplete=true}. */
    @SerializedName("confidence_threshold")
    public final float confidenceThreshold;

    /**
     * The participant's turn config over the defaults, or null for the defaults. Its
     * end-of-turn threshold replaces {@link #confidenceThreshold}.
     *
     * @since 1.6.0
     */
    @SerializedName("turns")
    public final @Nullable TurnConfigUpdate turns;

    /**
     * Construct a turn-detection configuration with the given parameters.
     *
     * @param bufferedSamples     audio (16 kHz samples) heard before turn detection decides
     * @param confidenceThreshold threshold in {@code [0.0, 1.0]} for turn-complete classification
     */
    public TurnDetectionConfig(int bufferedSamples, float confidenceThreshold) {
        this(bufferedSamples, confidenceThreshold, null);
    }

    private TurnDetectionConfig(int bufferedSamples, float confidenceThreshold,
                            @Nullable TurnConfigUpdate turns) {
        this.bufferedSamples = bufferedSamples;
        this.confidenceThreshold = confidenceThreshold;
        this.turns = turns;
    }

    /**
     * A copy whose participant starts with {@code turns} applied to its turn config (an
     * out-of-range value fails the add with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}).
     *
     * @param turns the turn config changes
     * @return a copy with {@link #turns} set
     * @since 1.6.0
     */
    public TurnDetectionConfig withTurns(TurnConfigUpdate turns) {
        return new TurnDetectionConfig(bufferedSamples, confidenceThreshold, turns);
    }

    /**
     * Returns the turn-detection config the engine applies as given:
     * {@code bufferedSamples=4000} (250 ms, the model's minimum) and
     * {@code confidenceThreshold=0.5}.
     *
     * @return default turn-detection configuration
     */
    public static TurnDetectionConfig defaults() {
        return new TurnDetectionConfig(4000, 0.5f);
    }
}
