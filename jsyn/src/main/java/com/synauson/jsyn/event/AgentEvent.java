package com.synauson.jsyn.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One event on a participant's voice-agent event stream (preview).
 *
 * <p>Emitted on the stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamAgentEvents}. The subclasses
 * are the event kinds: {@link Subscribed}, {@link Heartbeat}, {@link Error},
 * {@link StreamEnded}, {@link SpeechStarted} and {@link SpeechStopped}. The engine
 * adds kinds over time (turns come next); an older jsyn receives those as
 * {@link Unknown}, so handle the kinds you know and ignore the rest.
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

    /** First on every subscription. Not stored. */
    public static final class Subscribed extends AgentEvent {
        /** The oldest event the stream still keeps, 0 when it keeps none. */
        public final long oldestSeq;
        /** The stream's newest seq when the subscription started, 0 before its first event. */
        public final long lastSeq;
        /** Whether STT runs on the participant. */
        public final boolean stt;

        Subscribed(JsonObject o) {
            super(o);
            this.oldestSeq = number(o, "oldestSeq");
            this.lastSeq = number(o, "lastSeq");
            this.stt = o.has("stt") && o.get("stt").getAsBoolean();
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
        /** Stable, upper-case reason. */
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
