package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * Streaming speech-to-text configuration.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code SttConfigInternal}:
 * snake_case field names ({@code turn_drain_ms}). An unset field is left out, so the
 * engine applies its default.
 *
 * <p>STT needs turn detection on the same participant: each
 * {@link com.synauson.jsyn.event.TranscriptEvent.Turn} closes where turn detection says the
 * turn ended. The license must include {@code FEATURE_STT} as well as
 * {@code FEATURE_TURN_DETECTION}.
 *
 * @since 1.6.0
 */
public final class SttConfig {
    /**
     * How long a turn's transcript waits, after turn detection's decision, for the
     * transcription to reach the turn's end: 0 to 10000 ms of wall time. {@code null}
     * uses the engine's default (1000 ms). A turn that runs out of time is sent with
     * {@code complete == false}, and its late words open the next turn.
     */
    @SerializedName("turn_drain_ms")
    public final @Nullable Integer turnDrainMs;

    /**
     * Construct an STT configuration.
     *
     * @param turnDrainMs drain budget in ms (0 to 10000), or {@code null} for the default
     */
    public SttConfig(@Nullable Integer turnDrainMs) {
        this.turnDrainMs = turnDrainMs;
    }

    /**
     * Returns an STT configuration with the engine's defaults.
     *
     * @return default STT configuration
     */
    public static SttConfig defaults() {
        return new SttConfig(null);
    }
}
