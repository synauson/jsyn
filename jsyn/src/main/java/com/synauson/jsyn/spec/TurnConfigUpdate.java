package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * Changes to a participant's turn config: how its voice-agent turns end. Each field
 * set replaces the current one; a {@code null} field keeps it.
 *
 * <p>Used at the start, as {@link TurnDetectionConfig#withTurns}, and mid-call with
 * {@link com.synauson.jsyn.participant.Conference#updateTurnConfig}. Thresholds are in
 * {@code [0, 1]}, {@code eagerThreshold} at most {@code endOfTurnThreshold}, and
 * {@code endOfTurnTimeoutMs} 0 to 60000; the engine refuses anything else with an
 * {@link com.synauson.jsyn.exception.InvalidArgumentException}. Turning eager end of
 * turn on for a participant with STT needs {@link com.synauson.jsyn.JSynConfig.Builder#sttEager}
 * on the runtime (the engine reserves decoding for its forecasts); without it the engine
 * refuses with an {@code InvalidArgumentException}. Serializes with snake_case keys ({@code end_of_turn_threshold}),
 * leaving out unset fields. Immutable; the {@code with*} methods return a copy.
 *
 * @since 1.6.0
 */
public final class TurnConfigUpdate {
    /**
     * Turn detection's probability at or above which a pause ends the turn. Starts at
     * {@link TurnDetectionConfig#confidenceThreshold}.
     */
    @SerializedName("end_of_turn_threshold")
    public final @Nullable Float endOfTurnThreshold;

    /**
     * Eager end of turn: send an {@link com.synauson.jsyn.event.AgentEvent.EagerEndOfTurn}
     * when a pause probably ends the turn, before turn detection confirms it. Starts off.
     */
    @SerializedName("eager")
    public final @Nullable Boolean eager;

    /**
     * Turn detection's probability at or above which a pause sends the eager end; 0 sends it
     * at the speech end, before turn detection decides. At most {@link #endOfTurnThreshold}.
     * Starts at 0.
     */
    @SerializedName("eager_threshold")
    public final @Nullable Float eagerThreshold;

    /**
     * Silence after a speech end, in ms, that ends the turn anyway (reason
     * {@code TIMEOUT}); 0 turns it off. Starts at 5000.
     */
    @SerializedName("end_of_turn_timeout_ms")
    public final @Nullable Integer endOfTurnTimeoutMs;

    private TurnConfigUpdate(@Nullable Float endOfTurnThreshold, @Nullable Boolean eager,
                             @Nullable Float eagerThreshold, @Nullable Integer endOfTurnTimeoutMs) {
        this.endOfTurnThreshold = endOfTurnThreshold;
        this.eager = eager;
        this.eagerThreshold = eagerThreshold;
        this.endOfTurnTimeoutMs = endOfTurnTimeoutMs;
    }

    /**
     * An update that changes nothing.
     *
     * @return an empty update
     */
    public static TurnConfigUpdate none() {
        return new TurnConfigUpdate(null, null, null, null);
    }

    /**
     * @param threshold the end-of-turn threshold, in {@code [0, 1]}
     * @return a copy with the end-of-turn threshold set
     */
    public TurnConfigUpdate withEndOfTurnThreshold(float threshold) {
        return new TurnConfigUpdate(threshold, eager, eagerThreshold, endOfTurnTimeoutMs);
    }

    /**
     * @param on eager end of turn; only {@code false} is accepted for now
     * @return a copy with eager set
     */
    public TurnConfigUpdate withEager(boolean on) {
        return new TurnConfigUpdate(endOfTurnThreshold, on, eagerThreshold, endOfTurnTimeoutMs);
    }

    /**
     * @param threshold the eager threshold, in {@code [0, 1]}
     * @return a copy with the eager threshold set
     */
    public TurnConfigUpdate withEagerThreshold(float threshold) {
        return new TurnConfigUpdate(endOfTurnThreshold, eager, threshold, endOfTurnTimeoutMs);
    }

    /**
     * @param timeoutMs the end-of-turn timeout in ms, 0 to 60000; 0 turns it off
     * @return a copy with the end-of-turn timeout set
     */
    public TurnConfigUpdate withEndOfTurnTimeoutMs(int timeoutMs) {
        return new TurnConfigUpdate(endOfTurnThreshold, eager, eagerThreshold, timeoutMs);
    }
}
