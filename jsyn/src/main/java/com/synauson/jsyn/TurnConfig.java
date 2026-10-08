package com.synauson.jsyn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * How a participant's voice-agent turns end, as the engine applies it: every field set.
 * Reported by {@link com.synauson.jsyn.event.AgentEvent.Subscribed},
 * {@link com.synauson.jsyn.event.AgentEvent.TurnConfigUpdated} and
 * {@link com.synauson.jsyn.participant.Conference#updateTurnConfig}; change it with a
 * {@link com.synauson.jsyn.spec.TurnConfigUpdate}.
 *
 * @since 1.6.0
 */
public final class TurnConfig {
    /** Turn detection's probability at or above which a pause ends the turn. */
    public final float endOfTurnThreshold;
    /** Eager end of turn; always false for now. */
    public final boolean eager;
    /** Kept for eager end of turn; at most {@link #endOfTurnThreshold}. */
    public final float eagerThreshold;
    /** Silence after a speech end, in ms, that ends the turn anyway; 0 is off. */
    public final int endOfTurnTimeoutMs;

    private TurnConfig(JsonObject o) {
        this.endOfTurnThreshold = o.has("endOfTurnThreshold") ? o.get("endOfTurnThreshold").getAsFloat() : 0f;
        this.eager = o.has("eager") && o.get("eager").getAsBoolean();
        this.eagerThreshold = o.has("eagerThreshold") ? o.get("eagerThreshold").getAsFloat() : 0f;
        this.endOfTurnTimeoutMs = o.has("endOfTurnTimeoutMs") ? o.get("endOfTurnTimeoutMs").getAsInt() : 0;
    }

    /**
     * Read a config from the engine's JSON, or null when absent (an older engine).
     *
     * @param json the config object, or null
     * @return the config, or null
     */
    public static @Nullable TurnConfig fromJson(@Nullable JsonElement json) {
        return json == null || !json.isJsonObject() ? null : new TurnConfig(json.getAsJsonObject());
    }

    @Override
    public String toString() {
        return "TurnConfig{endOfTurnThreshold=" + endOfTurnThreshold + ", eager=" + eager
            + ", eagerThreshold=" + eagerThreshold + ", endOfTurnTimeoutMs=" + endOfTurnTimeoutMs + "}";
    }
}
