package com.synauson.jsyn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What {@link com.synauson.jsyn.participant.Conference#updateTurnConfig} returns: the
 * config now in effect, and the seq of the
 * {@link com.synauson.jsyn.event.AgentEvent.TurnConfigUpdated} event that announced it
 * on the participant's agent stream.
 *
 * @since 1.6.0
 */
public final class AppliedTurnConfig {
    /** The config now in effect. */
    public final TurnConfig config;
    /** The seq of its {@code TurnConfigUpdated} event. */
    public final long seq;

    private AppliedTurnConfig(TurnConfig config, long seq) {
        this.config = config;
        this.seq = seq;
    }

    /**
     * Read the engine's answer, {@code {"config":{...},"seq":n}}.
     *
     * @param json the engine's JSON
     * @return the applied config
     */
    public static AppliedTurnConfig fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        TurnConfig config = TurnConfig.fromJson(o.get("config"));
        if (config == null) {
            throw new com.google.gson.JsonParseException("no config in " + json);
        }
        return new AppliedTurnConfig(config, o.get("seq").getAsLong());
    }
}
