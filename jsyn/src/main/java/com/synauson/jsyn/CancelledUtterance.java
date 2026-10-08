package com.synauson.jsyn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One utterance that {@link com.synauson.jsyn.participant.Conference#cancelUtterance}
 * stopped: what the caller heard of it, and the seq of its
 * {@link com.synauson.jsyn.event.AgentEvent.UtteranceInterrupted} (reason
 * {@code CANCELLED}), which is on the agent stream before the call returns.
 *
 * @since 1.6.0
 */
public final class CancelledUtterance {
    /** The utterance. */
    public final String utteranceId;
    /**
     * Its text up to the end of its last fully played word, as it was sent; a prefix of
     * the text, so {@code heardText.length()} is the char index where the unheard text
     * starts.
     */
    public final String heardText;
    /** How much of its audio played, in ms (a duration), partly heard words included. */
    public final long heardMs;
    /** The seq of its {@code UtteranceInterrupted} on the agent stream. */
    public final long seq;

    private CancelledUtterance(JsonObject o) {
        this.utteranceId = o.get("utteranceId").getAsString();
        this.heardText = o.get("heardText").getAsString();
        this.heardMs = o.get("heardMs").getAsLong();
        this.seq = o.get("seq").getAsLong();
    }

    /**
     * Read the engine's answer, {@code {"cancelled":[{"utteranceId","heardText","heardMs","seq"}]}}.
     *
     * @param json the engine's JSON
     * @return the cancelled utterances, in order; empty when none was
     */
    public static List<CancelledUtterance> listFromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        JsonElement cancelled = o.get("cancelled");
        if (cancelled == null || !cancelled.isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = cancelled.getAsJsonArray();
        List<CancelledUtterance> out = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            out.add(new CancelledUtterance(e.getAsJsonObject()));
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public String toString() {
        return "CancelledUtterance{" + utteranceId + " heard \"" + heardText + "\" "
            + heardMs + " ms, seq " + seq + "}";
    }
}
