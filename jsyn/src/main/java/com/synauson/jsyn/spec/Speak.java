package com.synauson.jsyn.spec;

import com.synauson.jsyn.internal.Args;
import org.jspecify.annotations.Nullable;

/**
 * One {@link com.synauson.jsyn.participant.Conference#speak} call: text for an utterance
 * of a participant's speaker.
 *
 * <p>A new {@link #utteranceId} (1 to 256 bytes, never reused on the speaker) starts an
 * utterance; the same id adds text to it until it ends. {@link #text} is written text, at
 * most 16 KiB per Speak and 64 KiB per utterance; the speaker normalises it (numbers,
 * money, dates and codes are read as a person would) and holds back what could still
 * change, such as a number still being written. {@link #release} says what the text
 * ends. {@link #interruptible}, {@link #preemptible}, {@link #voice} and {@link #speed}
 * apply from an utterance's first Speak only: a later Speak with a voice or speed throws
 * {@link com.synauson.jsyn.exception.InvalidArgumentException} ({@code INVALID_SPEAK}),
 * and its {@code interruptible} and {@code preemptible} are ignored.
 *
 * <p>For a whole sentence at once, {@link #complete(String, String)}. To stream an LLM's
 * output, send each piece with {@link Release#NONE} and the last with {@link Release#END}.
 *
 * <p>Serializes to the engine's camelCase JSON ({@code utteranceId}); unset options are
 * left out.
 *
 * @since 1.6.0
 */
public final class Speak {
    /** What ends a Speak's text. */
    public enum Release {
        /** More text may follow; the speaker holds what could still change. */
        NONE,
        /** Speak everything sent so far now; the utterance stays open. */
        FLUSH,
        /**
         * The utterance's text is complete; its
         * {@link com.synauson.jsyn.event.AgentEvent.UtteranceDone} follows its playback.
         */
        END
    }

    /** The utterance this text belongs to. */
    public final String utteranceId;
    /** The text to add; may be empty (with {@link Release#END}, say). */
    public final String text;
    /** What the text ends. */
    public final Release release;
    /**
     * Whether {@code cancelUtterance} may stop the utterance. {@code null}: true. First
     * Speak only.
     */
    public final @Nullable Boolean interruptible;
    /**
     * Whether the next new utterance stops this one (reason {@code PREEMPTED}).
     * {@code null}: false. First Speak only.
     */
    public final @Nullable Boolean preemptible;
    /** A voice for this utterance; {@code null}: the speaker's. First Speak only. */
    public final @Nullable String voice;
    /** A rate for this utterance, 0.25 to 4; {@code null}: the speaker's. First Speak only. */
    public final @Nullable Float speed;

    private Speak(Builder b) {
        this.utteranceId = Args.notNull(b.utteranceId, "utteranceId");
        this.text = b.text;
        this.release = b.release;
        this.interruptible = b.interruptible;
        this.preemptible = b.preemptible;
        this.voice = b.voice;
        this.speed = b.speed;
    }

    /**
     * A whole utterance in one Speak: {@code text} with {@link Release#END}.
     *
     * @param utteranceId a new utterance id
     * @param text        everything the utterance says
     * @return the Speak
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if an argument is null
     */
    public static Speak complete(String utteranceId, String text) {
        return builder().utteranceId(utteranceId).text(Args.notNull(text, "text"))
            .release(Release.END).build();
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link Speak}. {@code utteranceId} is required; {@code text}
     * defaults to empty and {@code release} to {@link Release#NONE}.
     *
     * @since 1.6.0
     */
    public static final class Builder {
        private @Nullable String utteranceId;
        private String text = "";
        private Release release = Release.NONE;
        private @Nullable Boolean interruptible;
        private @Nullable Boolean preemptible;
        private @Nullable String voice;
        private @Nullable Float speed;

        /**
         * @param utteranceId the utterance; required
         * @return this builder
         */
        public Builder utteranceId(String utteranceId) { this.utteranceId = utteranceId; return this; }

        /**
         * @param text the text to add; non-null
         * @return this builder
         */
        public Builder text(String text) { this.text = Args.notNull(text, "text"); return this; }

        /**
         * @param release what the text ends; non-null
         * @return this builder
         */
        public Builder release(Release release) { this.release = Args.notNull(release, "release"); return this; }

        /**
         * @param interruptible whether {@code cancelUtterance} may stop it, or {@code null}
         * @return this builder
         */
        public Builder interruptible(@Nullable Boolean interruptible) { this.interruptible = interruptible; return this; }

        /**
         * @param preemptible whether the next new utterance stops it, or {@code null}
         * @return this builder
         */
        public Builder preemptible(@Nullable Boolean preemptible) { this.preemptible = preemptible; return this; }

        /**
         * @param voice a voice for this utterance, or {@code null} for the speaker's
         * @return this builder
         */
        public Builder voice(@Nullable String voice) { this.voice = voice; return this; }

        /**
         * @param speed a rate for this utterance (0.25 to 4), or {@code null} for the speaker's
         * @return this builder
         */
        public Builder speed(@Nullable Float speed) { this.speed = speed; return this; }

        /**
         * Materialise an immutable {@link Speak}.
         *
         * @return the configured Speak
         * @throws com.synauson.jsyn.exception.InvalidArgumentException if
         *         {@code utteranceId} is missing
         */
        public Speak build() {
            Args.required("Speak").field("utteranceId", utteranceId).validate();
            return new Speak(this);
        }
    }
}
