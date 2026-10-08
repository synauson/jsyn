package com.synauson.jsyn.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One transcribed word of a voice-agent turn, in {@link AgentEvent.TurnWords} and
 * {@link AgentEvent.EndOfTurn}.
 *
 * <p>Its times are conference time and are when the STT model emitted the word, not where
 * it was spoken: they trail the audio by up to the model's commit delay (about 800 ms for
 * the default model before a pause, less in running speech).
 *
 * @since 1.6.0
 */
public final class Word {
    /** The word with its punctuation and no leading space, such as {@code "Hello,"}. */
    public final String text;
    /** Conference time its first piece was emitted. */
    public final long startMs;
    /** Conference time its last piece ends. */
    public final long endMs;
    /**
     * The model's lowest probability for the word's pieces, in [0, 1]; null when the model
     * gives none. A relative score (a low one flags a word worth confirming), not a
     * calibrated probability of being right.
     */
    public final @Nullable Float confidence;

    Word(JsonObject o) {
        this.text = AgentEvent.string(o, "text");
        this.startMs = AgentEvent.number(o, "startMs");
        this.endMs = AgentEvent.number(o, "endMs");
        JsonElement c = o.get("confidence");
        this.confidence = c == null || c.isJsonNull() ? null : c.getAsFloat();
    }

    /** The words of a JSON array; empty when {@code json} is absent or not an array. */
    static List<Word> listFromJson(@Nullable JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = json.getAsJsonArray();
        List<Word> words = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            if (e.isJsonObject()) {
                words.add(new Word(e.getAsJsonObject()));
            }
        }
        return Collections.unmodifiableList(words);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof Word)) {
            return false;
        }
        Word w = (Word) other;
        return text.equals(w.text) && startMs == w.startMs && endMs == w.endMs
            && Objects.equals(confidence, w.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, startMs, endMs, confidence);
    }

    @Override
    public String toString() {
        return "Word{" + text + " " + startMs + ".." + endMs + " " + confidence + "}";
    }
}
