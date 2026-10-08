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
 * One fully played word of a speaker's utterance, in {@link AgentEvent.WordsPlayed}.
 *
 * <p>{@link #textStart} and {@link #textEnd} are offsets in the utterance's text
 * (everything its Speaks sent, concatenated) counted in <b>Unicode code points</b>, not
 * the UTF-16 chars a Java {@code String} indexes. They differ only for text outside the
 * Basic Multilingual Plane, such as an emoji (one code point, two chars). Use
 * {@link #charStart(String)} and {@link #charEnd(String)} to get char indexes:
 *
 * <pre>{@code
 * String spoken = utteranceText.substring(word.charStart(utteranceText),
 *                                         word.charEnd(utteranceText));
 * }</pre>
 *
 * @since 1.6.0
 */
public final class PlayedWord {
    /**
     * The word as it was sent, punctuation attached ({@code "Hello,"}); an entity read as
     * several words, such as {@code "$42.50"}, is one.
     */
    public final String text;
    /** Where it starts in the utterance's text, in code points. */
    public final long textStart;
    /** Where it ends (exclusive) in the utterance's text, in code points. */
    public final long textEnd;
    /**
     * Where it starts sounding in the utterance's own audio, in ms from the utterance's
     * first sample (a duration; gaps in playback don't move it).
     */
    public final long startMs;
    /** Where it stops sounding in the utterance's own audio, in ms from its first sample. */
    public final long endMs;

    PlayedWord(JsonObject o) {
        this.text = AgentEvent.string(o, "text");
        this.textStart = AgentEvent.number(o, "textStart");
        this.textEnd = AgentEvent.number(o, "textEnd");
        this.startMs = AgentEvent.number(o, "startMs");
        this.endMs = AgentEvent.number(o, "endMs");
    }

    /**
     * {@link #textStart} as a char index into the utterance's text.
     *
     * @param utteranceText everything the utterance's Speaks sent, concatenated
     * @return the index of the word's first char
     * @throws IndexOutOfBoundsException if the text is shorter than the offset
     */
    public int charStart(String utteranceText) {
        return utteranceText.offsetByCodePoints(0, Math.toIntExact(textStart));
    }

    /**
     * {@link #textEnd} as a char index into the utterance's text.
     *
     * @param utteranceText everything the utterance's Speaks sent, concatenated
     * @return the index just past the word's last char
     * @throws IndexOutOfBoundsException if the text is shorter than the offset
     */
    public int charEnd(String utteranceText) {
        return utteranceText.offsetByCodePoints(0, Math.toIntExact(textEnd));
    }

    /** The words of a JSON array; empty when {@code json} is absent or not an array. */
    static List<PlayedWord> listFromJson(@Nullable JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = json.getAsJsonArray();
        List<PlayedWord> words = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            if (e.isJsonObject()) {
                words.add(new PlayedWord(e.getAsJsonObject()));
            }
        }
        return Collections.unmodifiableList(words);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof PlayedWord)) {
            return false;
        }
        PlayedWord w = (PlayedWord) other;
        return text.equals(w.text) && textStart == w.textStart && textEnd == w.textEnd
            && startMs == w.startMs && endMs == w.endMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, textStart, textEnd, startMs, endMs);
    }

    @Override
    public String toString() {
        return "PlayedWord{" + text + " [" + textStart + ".." + textEnd + ") "
            + startMs + ".." + endMs + "}";
    }
}
