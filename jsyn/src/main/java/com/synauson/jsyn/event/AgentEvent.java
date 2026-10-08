package com.synauson.jsyn.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synauson.jsyn.TurnConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One event on a participant's voice-agent event stream.
 *
 * <p>Emitted on the stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamAgentEvents}. The subclasses
 * are the event kinds: {@link Subscribed}, {@link Heartbeat}, {@link Error},
 * {@link StreamEnded}, {@link SpeechStarted}, {@link SpeechStopped},
 * {@link TurnStarted}, {@link TurnWords}, {@link EndOfTurn}, {@link TurnConfigUpdated},
 * and, for a participant with a speaker, {@link UtteranceStarted}, {@link WordsPlayed},
 * {@link UtteranceDone}, {@link UtteranceInterrupted} and {@link UtteranceFailed}. The
 * engine adds kinds over time (eager end of turn comes next); an older jsyn receives
 * those as {@link Unknown}, so handle the kinds you know and ignore the rest.
 *
 * <p><b>Turns.</b> Turn ids rise by one from 1. Each {@link TurnStarted} is followed by
 * exactly one {@link EndOfTurn} for it, and no event of a turn comes before the
 * previous turn's {@code EndOfTurn}. {@link SpeechStarted} and {@link SpeechStopped}
 * carry no turn and come at once. With STT a turn starts with its first word, and its
 * {@link TurnWords}, in order, are exactly its {@code EndOfTurn}'s words.
 *
 * <p><b>Playback.</b> With a speaker
 * ({@link com.synauson.jsyn.spec.TtsConfig}), every utterance that plays any audio gets
 * one {@link UtteranceStarted}, then its {@link WordsPlayed}, then exactly one of
 * {@link UtteranceDone}, {@link UtteranceInterrupted} or {@link UtteranceFailed}; none of
 * its events follows that. One cancelled or failed before any of its audio played gets
 * only its {@code UtteranceInterrupted} or {@code UtteranceFailed}. Utterances play one
 * after another in the order they were started; match events to utterances by
 * {@code utteranceId}. "Played" means the audio passed the
 * speaker's clock-synced point, the last place the engine can still stop it, so
 * {@link UtteranceInterrupted#heardText} is exact.
 *
 * <p><b>Text offsets are Unicode code points.</b> {@link PlayedWord#textStart},
 * {@link PlayedWord#textEnd} and {@link UtteranceInterrupted#heardTextEnd} count code
 * points of the utterance's text (everything its Speaks sent, concatenated), while a
 * Java {@code String} indexes UTF-16 chars: text outside the Basic Multilingual Plane,
 * such as an emoji, takes two chars and one code point. Convert with
 * {@link PlayedWord#charStart(String)} and {@link PlayedWord#charEnd(String)}, which use
 * {@link String#offsetByCodePoints}; {@code heardText.length()} is already a char index.
 *
 * <p><b>Ordering and resume.</b> Every subscriber sees one order. {@link #seq} rises by
 * one with each stored event, from 1. {@link Subscribed} and {@link Heartbeat} belong to
 * one subscription, aren't stored, and repeat the last seq it has ({@link #isStored()}
 * is false for them). {@link #streamId} names the participant's stream: a participant
 * removed and added again under the same id gets a new one, with seqs from 1 again. To
 * resume after an error, subscribe with the last stored event's {@code streamId} and
 * {@code seq} ({@link com.synauson.jsyn.AgentStreamOptions#resumeAfter}); you get exactly
 * the events after it.
 *
 * <p><b>Conference time.</b> Fields ending in {@code Ms} that are not durations are the
 * conference's time: its pipeline's running time in ms, one clock for every participant
 * of the conference.
 *
 * <p>The engine hands each event over as a JSON object, read by {@link #fromJson}.
 *
 * @since 1.6.0
 */
public abstract class AgentEvent {
    /** Conference ID this event was emitted for. */
    public final String conferenceId;
    /** Participant ID this event was emitted for. */
    public final String participantId;
    /** The participant's stream; changes when the participant is added again. */
    public final long streamId;
    /**
     * Strictly increasing from 1 over stored events; {@link Subscribed} and
     * {@link Heartbeat} repeat the last seq the subscription has.
     */
    public final long seq;
    /** Wall-clock time the event was made, ms since the Unix epoch. */
    public final long timestampUnixMs;

    AgentEvent(JsonObject json) {
        this.conferenceId = string(json, "conferenceId");
        this.participantId = string(json, "participantId");
        this.streamId = number(json, "streamId");
        this.seq = number(json, "seq");
        this.timestampUnixMs = number(json, "timestampUnixMs");
    }

    /**
     * Whether the stream keeps this event for replay and gave it its own seq: false only
     * for {@link Subscribed} and {@link Heartbeat}. Resume after the last stored event.
     *
     * @return whether the event is stored
     */
    public boolean isStored() {
        return true;
    }

    /**
     * Parse one event from the engine's JSON.
     *
     * @param json one event, as the engine sends it
     * @return the event; {@link Unknown} for a kind this jsyn doesn't know
     * @throws com.google.gson.JsonParseException if {@code json} is not a JSON object
     */
    public static AgentEvent fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String type = o.has("type") ? o.get("type").getAsString() : "";
        switch (type) {
            case "subscribed": return new Subscribed(o);
            case "heartbeat": return new Heartbeat(o);
            case "error": return new Error(o);
            case "streamEnded": return new StreamEnded(o);
            case "speechStarted": return new SpeechStarted(o);
            case "speechStopped": return new SpeechStopped(o);
            case "turnStarted": return new TurnStarted(o);
            case "turnWords": return new TurnWords(o);
            case "endOfTurn": return new EndOfTurn(o);
            case "turnConfigUpdated": return new TurnConfigUpdated(o);
            case "utteranceStarted": return new UtteranceStarted(o);
            case "wordsPlayed": return new WordsPlayed(o);
            case "utteranceDone": return new UtteranceDone(o);
            case "utteranceInterrupted": return new UtteranceInterrupted(o);
            case "utteranceFailed": return new UtteranceFailed(o);
            default: return new Unknown(o, type, json);
        }
    }

    static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    static long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsLong();
    }

    static @Nullable Long optionalNumber(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsLong();
    }

    static @Nullable String optionalString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    /** First on every subscription. Not stored. */
    public static final class Subscribed extends AgentEvent {
        /** The oldest event the stream still keeps, 0 when it keeps none. */
        public final long oldestSeq;
        /** The stream's newest seq when the subscription started, 0 before its first event. */
        public final long lastSeq;
        /** Whether STT runs on the participant. */
        public final boolean stt;
        /** The turn config in effect; null from an engine older than turn events. */
        public final @Nullable TurnConfig turnConfig;
        /**
         * Whether the participant has a speaker ({@link com.synauson.jsyn.spec.TtsConfig});
         * false from an engine older than the speaker.
         *
         * @since 1.6.0
         */
        public final boolean tts;
        /**
         * The speaker's default voice; null without a speaker.
         *
         * @since 1.6.0
         */
        public final @Nullable String voice;

        Subscribed(JsonObject o) {
            super(o);
            this.oldestSeq = number(o, "oldestSeq");
            this.lastSeq = number(o, "lastSeq");
            this.stt = bool(o, "stt");
            this.turnConfig = TurnConfig.fromJson(o.get("turnConfig"));
            this.tts = bool(o, "tts");
            this.voice = optionalString(o, "voice");
        }

        @Override
        public boolean isStored() { return false; }
    }

    /** Sent when nothing else was for the subscription's heartbeat interval. Not stored. */
    public static final class Heartbeat extends AgentEvent {
        /** The conference's time now. */
        public final long conferenceMs;
        /** The conference time STT has transcribed up to; null without STT progress. */
        public final @Nullable Long sttDecodedMs;
        /** Audio waiting to be transcribed, in ms (a duration); null when not reported. */
        public final @Nullable Long sttBacklogMs;

        Heartbeat(JsonObject o) {
            super(o);
            this.conferenceMs = number(o, "conferenceMs");
            this.sttDecodedMs = optionalNumber(o, "sttDecodedMs");
            this.sttBacklogMs = optionalNumber(o, "sttBacklogMs");
        }

        @Override
        public boolean isStored() { return false; }
    }

    /**
     * A recoverable problem; the stream goes on. Stream-fatal errors end the stream with
     * {@link com.synauson.jsyn.exception.AgentStreamException} instead.
     */
    public static final class Error extends AgentEvent {
        /**
         * Turn detection stopped on the participant: turns end only by the timeout or
         * {@code forceEndTurn} from now on.
         */
        public static final String TURN_DETECTION_FAILED = "TURN_DETECTION_FAILED";
        /**
         * A speech end got no turn detection decision within 2 s; the end-of-turn timeout
         * still ends the turn.
         */
        public static final String TURN_DECISION_MISSING = "TURN_DECISION_MISSING";
        /**
         * STT failed on the participant: turns end with the words they had, and later
         * ones without words.
         */
        public static final String STT_STOPPED = "STT_STOPPED";
        /**
         * STT's backlog passed the participant's STT drain budget (at least 500 ms;
         * metadata {@code backlog_ms} and {@code threshold_ms}): turns may end before
         * their last words, which then open the next turn. Sent again only after the
         * backlog falls to half the threshold.
         */
        public static final String STT_LAGGING = "STT_LAGGING";

        /** Stable, upper-case reason, such as {@link #TURN_DECISION_MISSING}. */
        public final String reason;
        /** Human-readable description. */
        public final String message;
        /** Details, such as a backlog in ms. */
        public final Map<String, String> metadata;
        /** The turn it concerns, if any. */
        public final @Nullable Long turnId;

        Error(JsonObject o) {
            super(o);
            this.reason = string(o, "reason");
            this.message = string(o, "message");
            Map<String, String> m = new LinkedHashMap<>();
            JsonElement meta = o.get("metadata");
            if (meta != null && meta.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : meta.getAsJsonObject().entrySet()) {
                    m.put(e.getKey(), e.getValue().getAsString());
                }
            }
            this.metadata = Collections.unmodifiableMap(m);
            this.turnId = optionalNumber(o, "turnId");
        }
    }

    /** Last on the stream; {@code onCompleted} follows. */
    public static final class StreamEnded extends AgentEvent {
        /** The participant was removed. */
        public static final String PARTICIPANT_REMOVED = "PARTICIPANT_REMOVED";
        /** The conference was terminated. */
        public static final String CONFERENCE_TERMINATED = "CONFERENCE_TERMINATED";

        /** Why: {@link #PARTICIPANT_REMOVED}, {@link #CONFERENCE_TERMINATED}, or a newer reason. */
        public final String reason;

        StreamEnded(JsonObject o) {
            super(o);
            this.reason = string(o, "reason");
        }
    }

    /** VAD heard speech start. Raw voice activity: noise can start it too. */
    public static final class SpeechStarted extends AgentEvent {
        /** Conference time the speech started, before VAD's debounce. */
        public final long atMs;
        /** VAD's probability for the window that confirmed it. */
        public final float probability;

        SpeechStarted(JsonObject o) {
            super(o);
            this.atMs = number(o, "atMs");
            this.probability = o.has("probability") ? o.get("probability").getAsFloat() : 0f;
        }
    }

    /** VAD heard the speech stop. */
    public static final class SpeechStopped extends AgentEvent {
        /** Conference time the speech stopped, before VAD's debounce. */
        public final long atMs;
        /** How long it lasted, in ms (a duration): {@link #atMs} less the start's. */
        public final long speechMs;

        SpeechStopped(JsonObject o) {
            super(o);
            this.atMs = number(o, "atMs");
            this.speechMs = number(o, "speechMs");
        }
    }

    /**
     * A turn started: with STT on its first word, without STT on VAD's speech start. With
     * STT a turn without words is never sent.
     */
    public static final class TurnStarted extends AgentEvent {
        /** Rises by one from 1. */
        public final long turnId;
        /** Conference time the turn's speech started (VAD's speech start). */
        public final long startMs;
        /** Whether words started it: true with STT, false without. */
        public final boolean wordBacked;

        TurnStarted(JsonObject o) {
            super(o);
            this.turnId = number(o, "turnId");
            this.startMs = number(o, "startMs");
            this.wordBacked = bool(o, "wordBacked");
        }
    }

    /**
     * More of the open turn's words, each once it is complete: the model committed the
     * next word, or the turn closed. A word once sent is never taken back or moved to
     * another turn, and a turn's last words come just before its {@link EndOfTurn}.
     */
    public static final class TurnWords extends AgentEvent {
        /** The turn they belong to. */
        public final long turnId;
        /** The words, in order. */
        public final List<Word> words;
        /** Audio waiting to be transcribed, in ms (a duration); null when not reported. */
        public final @Nullable Long sttBacklogMs;

        TurnWords(JsonObject o) {
            super(o);
            this.turnId = number(o, "turnId");
            this.words = Word.listFromJson(o.get("words"));
            this.sttBacklogMs = optionalNumber(o, "sttBacklogMs");
        }
    }

    /** A turn ended: exactly one per {@link TurnStarted}, in turn order. */
    public static final class EndOfTurn extends AgentEvent {
        /** Turn detection's probability for a pause reached the end-of-turn threshold. */
        public static final String MODEL = "MODEL";
        /** {@code forceEndTurn} ended it. */
        public static final String MANUAL = "MANUAL";
        /** The participant was silent for the end-of-turn timeout after a speech end. */
        public static final String TIMEOUT = "TIMEOUT";
        /** The stream ended with the turn open. */
        public static final String STREAM_ENDED = "STREAM_ENDED";

        /** The turn's id. */
        public final long turnId;
        /** Why: {@link #MODEL}, {@link #MANUAL}, {@link #TIMEOUT}, {@link #STREAM_ENDED}, or a newer reason. */
        public final String reason;
        /**
         * The turn's words joined by single spaces with STT (the same text as its
         * {@link TranscriptEvent.Turn}, without the leading space); empty without STT.
         */
        public final String text;
        /**
         * The turn's words, in order: the ones its {@link TurnWords} carried. Empty without
         * STT, and from an engine older than words.
         */
        public final List<Word> words;
        /** Conference time the turn's speech started. */
        public final long startMs;
        /**
         * Conference time the turn's speech ended: VAD's last speech end, or when the turn
         * ended if the participant was still speaking.
         */
        public final long speechEndMs;
        /** Turn detection's probability for the turn's last pause; null when it didn't decide. */
        public final @Nullable Float probability;
        /**
         * False when {@link #text} may lack the turn's last words: STT ran out of drain
         * time, or stopped, before it settled.
         */
        public final boolean complete;
        /** How long the end took, in ms (durations); a field is null when not known. */
        public final @Nullable Long sinceSpeechEndMs;
        /** Turn detection's decision, queued plus inference; {@link #MODEL} ends only. */
        public final @Nullable Long decisionMs;
        /** Waiting for STT's text, with STT. */
        public final @Nullable Long drainMs;
        /** Audio waiting to be transcribed when the turn ended, with STT. */
        public final @Nullable Long sttBacklogMs;

        EndOfTurn(JsonObject o) {
            super(o);
            this.turnId = number(o, "turnId");
            this.reason = string(o, "reason");
            this.text = string(o, "text");
            this.words = Word.listFromJson(o.get("words"));
            this.startMs = number(o, "startMs");
            this.speechEndMs = number(o, "speechEndMs");
            JsonElement p = o.get("probability");
            this.probability = p == null || p.isJsonNull() ? null : p.getAsFloat();
            this.complete = bool(o, "complete");
            JsonElement l = o.get("latency");
            JsonObject latency = l != null && l.isJsonObject() ? l.getAsJsonObject() : new JsonObject();
            this.sinceSpeechEndMs = optionalNumber(latency, "sinceSpeechEndMs");
            this.decisionMs = optionalNumber(latency, "decisionMs");
            this.drainMs = optionalNumber(latency, "drainMs");
            this.sttBacklogMs = optionalNumber(latency, "sttBacklogMs");
        }
    }

    /** {@code updateTurnConfig} changed the turn config. */
    public static final class TurnConfigUpdated extends AgentEvent {
        /** The config now in effect. */
        public final @Nullable TurnConfig config;

        TurnConfigUpdated(JsonObject o) {
            super(o);
            this.config = TurnConfig.fromJson(o.get("config"));
        }
    }

    /**
     * The speaker's first sample of an utterance played into the call. Exactly one per
     * utterance that plays any audio, before its {@link WordsPlayed}.
     *
     * @since 1.6.0
     */
    public static final class UtteranceStarted extends AgentEvent {
        /** The utterance, as given to {@code speak}. */
        public final String utteranceId;
        /**
         * Conference time its first sample passed the speaker's clock-synced point, the
         * last place the engine can still stop it.
         */
        public final long atMs;
        /** From the utterance's first {@code speak} to {@link #atMs}, in ms (a duration). */
        public final long timeToFirstAudioMs;
        /**
         * The latency of the mixer the speaker plays to its participant through, in ms:
         * about how much later than {@link #atMs} the audio leaves the engine (the
         * network and the far end's jitter buffer come on top). Null when the speaker
         * plays through no mixer of its participant.
         */
        public final @Nullable Long egressDelayMs;

        UtteranceStarted(JsonObject o) {
            super(o);
            this.utteranceId = string(o, "utteranceId");
            this.atMs = number(o, "atMs");
            this.timeToFirstAudioMs = number(o, "timeToFirstAudioMs");
            this.egressDelayMs = optionalNumber(o, "egressDelayMs");
        }
    }

    /**
     * More of an utterance's words fully played, in order. An utterance's
     * {@code WordsPlayed}, joined, are the words of its text that were heard; none follows
     * its {@link UtteranceDone}, {@link UtteranceInterrupted} or {@link UtteranceFailed}.
     *
     * @since 1.6.0
     */
    public static final class WordsPlayed extends AgentEvent {
        /** The utterance they belong to. */
        public final String utteranceId;
        /** The words, in order. */
        public final List<PlayedWord> words;

        WordsPlayed(JsonObject o) {
            super(o);
            this.utteranceId = string(o, "utteranceId");
            this.words = PlayedWord.listFromJson(o.get("words"));
        }
    }

    /**
     * An utterance played into the call to its end.
     *
     * @since 1.6.0
     */
    public static final class UtteranceDone extends AgentEvent {
        /** The utterance. */
        public final String utteranceId;
        /** Its audio's length, in ms (a duration). */
        public final long audioMs;
        /** Conference time its last sample played. */
        public final long atMs;
        /**
         * Silence inside it while synthesis fell behind playback, in ms (a duration): dead
         * air the caller heard.
         */
        public final long underrunMs;

        UtteranceDone(JsonObject o) {
            super(o);
            this.utteranceId = string(o, "utteranceId");
            this.audioMs = number(o, "audioMs");
            this.atMs = number(o, "atMs");
            this.underrunMs = number(o, "underrunMs");
        }
    }

    /**
     * An utterance stopped before its end; nothing more of it plays.
     *
     * @since 1.6.0
     */
    public static final class UtteranceInterrupted extends AgentEvent {
        /** {@code cancelUtterance} stopped it. */
        public static final String CANCELLED = "CANCELLED";
        /** A new utterance started while it was preemptible. */
        public static final String PREEMPTED = "PREEMPTED";
        /**
         * The participant or conference went; comes before the stream's
         * {@link StreamEnded}.
         */
        public static final String STREAM_ENDED = "STREAM_ENDED";

        /** The utterance. */
        public final String utteranceId;
        /** Why: {@link #CANCELLED}, {@link #PREEMPTED}, {@link #STREAM_ENDED}, or a newer reason. */
        public final String reason;
        /**
         * The utterance's text up to the end of its last fully played word: what the
         * caller heard, as it was sent. A prefix of the text, so {@code heardText.length()}
         * is the char index where the unheard text starts.
         */
        public final String heardText;
        /** {@link #heardText}'s length in Unicode code points (not chars). */
        public final long heardTextEnd;
        /** How much of its audio played, in ms (a duration), partly heard words included. */
        public final long heardMs;

        UtteranceInterrupted(JsonObject o) {
            super(o);
            this.utteranceId = string(o, "utteranceId");
            this.reason = string(o, "reason");
            this.heardText = string(o, "heardText");
            this.heardTextEnd = number(o, "heardTextEnd");
            this.heardMs = number(o, "heardMs");
        }
    }

    /**
     * An utterance failed; nothing more of it plays.
     *
     * @since 1.6.0
     */
    public static final class UtteranceFailed extends AgentEvent {
        /** The speaker's TTS engine is gone. */
        public static final String TTS_UNAVAILABLE = "TTS_UNAVAILABLE";
        /** The model failed on its text. */
        public static final String SYNTHESIS_FAILED = "SYNTHESIS_FAILED";
        /** Its text has nothing to say, or a word too long for the model. */
        public static final String UNSPEAKABLE_TEXT = "UNSPEAKABLE_TEXT";
        /** This machine's TTS capacity was in use when it was to start. */
        public static final String TTS_CAPACITY = "TTS_CAPACITY";

        /** The utterance. */
        public final String utteranceId;
        /**
         * Why: {@link #TTS_UNAVAILABLE}, {@link #SYNTHESIS_FAILED},
         * {@link #UNSPEAKABLE_TEXT}, {@link #TTS_CAPACITY}, or a newer code.
         */
        public final String code;
        /** Human-readable description. */
        public final String message;

        UtteranceFailed(JsonObject o) {
            super(o);
            this.utteranceId = string(o, "utteranceId");
            this.code = string(o, "code");
            this.message = string(o, "message");
        }
    }

    /**
     * An event kind this jsyn doesn't know, from a newer engine. Its envelope fields are
     * read; {@link #json} holds the whole event. It still counts for resume.
     */
    public static final class Unknown extends AgentEvent {
        /** The engine's name for the kind. */
        public final String type;
        /** The event as the engine sent it. */
        public final String json;

        Unknown(JsonObject o, String type, String json) {
            super(o);
            this.type = type;
            this.json = json;
        }
    }
}
