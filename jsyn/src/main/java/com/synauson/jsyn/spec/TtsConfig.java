package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * A speaker on a participant: text-to-speech that
 * {@link com.synauson.jsyn.participant.Conference#speak} sends into the call.
 *
 * <p>The speaker plays to its own participant (SIP, WebRTC and native participants; a
 * file participant has no output, so its speaker plays only where it is routed). It is
 * also a routing source of its own, {@link #speakerId}, for
 * {@link com.synauson.jsyn.participant.Conference#updatePartyAudioConnections} (to a
 * recording, say); a route from it to its own participant is refused with an
 * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
 *
 * <p>A speaker needs turn detection on the same participant: what it plays is reported on
 * the participant's agent stream
 * ({@link com.synauson.jsyn.event.AgentEvent.UtteranceStarted} and the rest). The
 * license must include {@code FEATURE_TTS}, and
 * {@link com.synauson.jsyn.Capabilities#tts} must be {@code ready} when the participant
 * is added, or the add throws
 * {@link com.synauson.jsyn.exception.FailedPreconditionException}.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code TtsConfigInternal}:
 * snake_case field names ({@code speaker_id}). An unset field is left out, so the
 * engine applies its default. Immutable; the {@code with*} methods return a copy.
 *
 * @since 1.6.0
 */
public final class TtsConfig {
    /**
     * The speaker's default voice, one of the model's voices such as {@code en-us-f1} or
     * {@code en-us-m1}. {@code null}: {@code en-us-f1}. An unknown voice fails the add with
     * an {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     */
    public final @Nullable String voice;

    /**
     * The speaker's default rate, 0.25 to 4 (1 is the voice's own). {@code null}: 1.
     */
    public final @Nullable Float speed;

    /**
     * The speaker's id as a routing source. {@code null}:
     * {@code <participant id>.speaker}.
     */
    @SerializedName("speaker_id")
    public final @Nullable String speakerId;

    private TtsConfig(@Nullable String voice, @Nullable Float speed, @Nullable String speakerId) {
        this.voice = voice;
        this.speed = speed;
        this.speakerId = speakerId;
    }

    /**
     * A speaker with the engine's defaults.
     *
     * @return default TTS configuration
     */
    public static TtsConfig defaults() {
        return new TtsConfig(null, null, null);
    }

    /**
     * @param voice the default voice, such as {@code en-us-m1}
     * @return a copy with the default voice set
     */
    public TtsConfig withVoice(String voice) {
        return new TtsConfig(voice, speed, speakerId);
    }

    /**
     * @param speed the default rate, 0.25 to 4
     * @return a copy with the default speed set
     */
    public TtsConfig withSpeed(float speed) {
        return new TtsConfig(voice, speed, speakerId);
    }

    /**
     * @param speakerId the speaker's id as a routing source; not empty
     * @return a copy with the speaker id set
     */
    public TtsConfig withSpeakerId(String speakerId) {
        return new TtsConfig(voice, speed, speakerId);
    }
}
