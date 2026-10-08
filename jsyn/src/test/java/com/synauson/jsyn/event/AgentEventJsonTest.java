package com.synauson.jsyn.event;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synauson.jsyn.AgentStreamOptions;
import com.synauson.jsyn.AppliedTurnConfig;
import com.synauson.jsyn.CancelledUtterance;
import com.synauson.jsyn.TurnConfig;
import com.synauson.jsyn.exception.AgentStreamException;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.Speak;
import com.synauson.jsyn.spec.TurnConfigUpdate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The engine's agent event JSON (serde, camelCase, the kind in {@code type}). The engine
 * pins the same keys in its {@code json_keys_match_the_java_client} test.
 */
class AgentEventJsonTest {
    private static final String ENVELOPE = "\"conferenceId\":\"c\",\"participantId\":\"p\","
        + "\"streamId\":4503599627370495,\"timestampUnixMs\":1700000000000,";

    private static AgentEvent parse(long seq, String rest) {
        return AgentEvent.fromJson("{" + ENVELOPE + "\"seq\":" + seq + "," + rest + "}");
    }

    @Test
    void readsTheEnvelope() {
        AgentEvent e = parse(3, "\"type\":\"speechStarted\",\"atMs\":1056,\"probability\":0.99");
        assertEquals("c", e.conferenceId);
        assertEquals("p", e.participantId);
        assertEquals(4503599627370495L, e.streamId, "stream ids use up to 53 bits");
        assertEquals(3, e.seq);
        assertEquals(1700000000000L, e.timestampUnixMs);
        assertTrue(e.isStored());
        AgentEvent.SpeechStarted s = assertInstanceOf(AgentEvent.SpeechStarted.class, e);
        assertEquals(1056, s.atMs);
        assertEquals(0.99f, s.probability, 1e-6);
    }

    @Test
    void readsEveryKind() {
        AgentEvent.Subscribed sub = assertInstanceOf(AgentEvent.Subscribed.class,
            parse(0, "\"type\":\"subscribed\",\"oldestSeq\":1,\"lastSeq\":4,\"stt\":true,"
                + "\"turnConfig\":" + CONFIG));
        assertEquals(1, sub.oldestSeq);
        assertEquals(4, sub.lastSeq);
        assertTrue(sub.stt);
        assertFalse(sub.isStored());
        assertConfig(sub.turnConfig);
        AgentEvent.Subscribed older = assertInstanceOf(AgentEvent.Subscribed.class,
            parse(0, "\"type\":\"subscribed\",\"oldestSeq\":0,\"lastSeq\":0,\"stt\":false"));
        assertNull(older.turnConfig, "an engine before turn events");
        assertFalse(older.tts, "an engine before the speaker");
        assertNull(older.voice);
        AgentEvent.Subscribed speaking = assertInstanceOf(AgentEvent.Subscribed.class,
            parse(0, "\"type\":\"subscribed\",\"oldestSeq\":1,\"lastSeq\":4,\"stt\":true,"
                + "\"tts\":true,\"turnConfig\":" + CONFIG + ",\"voice\":\"en-us-f1\""));
        assertTrue(speaking.tts);
        assertEquals("en-us-f1", speaking.voice);
        assertFalse(sub.tts, "no tts key: an engine before the speaker");

        AgentEvent.Heartbeat beat = assertInstanceOf(AgentEvent.Heartbeat.class,
            parse(4, "\"type\":\"heartbeat\",\"conferenceMs\":12000,"
                + "\"sttDecodedMs\":11000,\"sttBacklogMs\":80"));
        assertEquals(12000, beat.conferenceMs);
        assertEquals(Long.valueOf(11000), beat.sttDecodedMs);
        assertEquals(Long.valueOf(80), beat.sttBacklogMs);
        assertFalse(beat.isStored());
        AgentEvent.Heartbeat quiet = assertInstanceOf(AgentEvent.Heartbeat.class,
            parse(4, "\"type\":\"heartbeat\",\"conferenceMs\":12000"));
        assertNull(quiet.sttDecodedMs, "absent without STT");
        assertNull(quiet.sttBacklogMs);

        AgentEvent.Error err = assertInstanceOf(AgentEvent.Error.class,
            parse(5, "\"type\":\"error\",\"reason\":\"STT_LAGGING\",\"message\":\"behind\","
                + "\"metadata\":{\"backlog_ms\":\"900\"},\"turnId\":3"));
        assertEquals(AgentEvent.Error.STT_LAGGING, err.reason);
        assertEquals("behind", err.message);
        assertEquals("900", err.metadata.get("backlog_ms"));
        assertEquals(Long.valueOf(3), err.turnId);
        AgentEvent.Error noTurn = assertInstanceOf(AgentEvent.Error.class,
            parse(5, "\"type\":\"error\",\"reason\":\"X\",\"message\":\"m\",\"metadata\":{}"));
        assertNull(noTurn.turnId);
        assertTrue(noTurn.metadata.isEmpty());

        AgentEvent.StreamEnded end = assertInstanceOf(AgentEvent.StreamEnded.class,
            parse(6, "\"type\":\"streamEnded\",\"reason\":\"PARTICIPANT_REMOVED\""));
        assertEquals(AgentEvent.StreamEnded.PARTICIPANT_REMOVED, end.reason);

        AgentEvent.SpeechStopped stop = assertInstanceOf(AgentEvent.SpeechStopped.class,
            parse(2, "\"type\":\"speechStopped\",\"atMs\":2500,\"speechMs\":1500"));
        assertEquals(2500, stop.atMs);
        assertEquals(1500, stop.speechMs);

        AgentEvent.TurnStarted turn = assertInstanceOf(AgentEvent.TurnStarted.class,
            parse(3, "\"type\":\"turnStarted\",\"turnId\":1,\"startMs\":1000,\"wordBacked\":true"));
        assertEquals(1, turn.turnId);
        assertEquals(1000, turn.startMs);
        assertTrue(turn.wordBacked);

        AgentEvent.TurnWords words = assertInstanceOf(AgentEvent.TurnWords.class,
            parse(4, "\"type\":\"turnWords\",\"turnId\":1,\"words\":[" + HELLO + "],"
                + "\"sttBacklogMs\":40"));
        assertEquals(1, words.turnId);
        assertEquals(1, words.words.size());
        assertHello(words.words.get(0));
        assertEquals(Long.valueOf(40), words.sttBacklogMs);
        AgentEvent.TurnWords unrated = assertInstanceOf(AgentEvent.TurnWords.class,
            parse(4, "\"type\":\"turnWords\",\"turnId\":1,\"words\":[{\"text\":\"hi\","
                + "\"startMs\":5,\"endMs\":6}]"));
        assertNull(unrated.words.get(0).confidence, "absent when the model gives none");
        assertNull(unrated.sttBacklogMs);

        AgentEvent.EndOfTurn end2 = assertInstanceOf(AgentEvent.EndOfTurn.class,
            parse(4, "\"type\":\"endOfTurn\",\"turnId\":1,\"reason\":\"MODEL\","
                + "\"text\":\"hello\",\"words\":[" + HELLO + "],\"startMs\":1000,\"speechEndMs\":2500,"
                + "\"probability\":0.75,\"complete\":true,\"latency\":{\"sinceSpeechEndMs\":400,"
                + "\"decisionMs\":30,\"drainMs\":250,\"sttBacklogMs\":80}"));
        assertEquals(1, end2.turnId);
        assertEquals(AgentEvent.EndOfTurn.MODEL, end2.reason);
        assertEquals("hello", end2.text);
        assertEquals(1, end2.words.size());
        assertHello(end2.words.get(0));
        assertEquals(words.words, end2.words, "words compare by value");
        assertEquals(1000, end2.startMs);
        assertEquals(2500, end2.speechEndMs);
        assertEquals(0.75f, end2.probability, 1e-6);
        assertTrue(end2.complete);
        assertEquals(Long.valueOf(400), end2.sinceSpeechEndMs);
        assertEquals(Long.valueOf(30), end2.decisionMs);
        assertEquals(Long.valueOf(250), end2.drainMs);
        assertEquals(Long.valueOf(80), end2.sttBacklogMs);
        AgentEvent.EndOfTurn bare = assertInstanceOf(AgentEvent.EndOfTurn.class,
            parse(4, "\"type\":\"endOfTurn\",\"turnId\":2,\"reason\":\"TIMEOUT\",\"text\":\"\","
                + "\"startMs\":3000,\"speechEndMs\":3500,\"complete\":true,\"latency\":{}"));
        assertEquals(AgentEvent.EndOfTurn.TIMEOUT, bare.reason);
        assertNull(bare.probability, "absent when turn detection didn't decide");
        assertNull(bare.decisionMs);
        assertNull(bare.drainMs);
        assertTrue(bare.words.isEmpty(), "no words key: an engine before words");

        AgentEvent.TurnConfigUpdated updated = assertInstanceOf(AgentEvent.TurnConfigUpdated.class,
            parse(5, "\"type\":\"turnConfigUpdated\",\"config\":" + CONFIG));
        assertConfig(updated.config);
    }

    @Test
    void readsThePlaybackEvents() {
        AgentEvent.UtteranceStarted started = assertInstanceOf(AgentEvent.UtteranceStarted.class,
            parse(6, "\"type\":\"utteranceStarted\",\"utteranceId\":\"u1\",\"atMs\":9000,"
                + "\"timeToFirstAudioMs\":310,\"egressDelayMs\":40"));
        assertEquals("u1", started.utteranceId);
        assertEquals(9000, started.atMs);
        assertEquals(310, started.timeToFirstAudioMs);
        assertEquals(Long.valueOf(40), started.egressDelayMs);
        assertTrue(started.isStored());
        AgentEvent.UtteranceStarted noMixer = assertInstanceOf(AgentEvent.UtteranceStarted.class,
            parse(6, "\"type\":\"utteranceStarted\",\"utteranceId\":\"u1\",\"atMs\":1,"
                + "\"timeToFirstAudioMs\":1"));
        assertNull(noMixer.egressDelayMs, "absent without a mixer to its participant");

        AgentEvent.WordsPlayed played = assertInstanceOf(AgentEvent.WordsPlayed.class,
            parse(7, "\"type\":\"wordsPlayed\",\"utteranceId\":\"u1\",\"words\":[{\"text\":\"Hello,\","
                + "\"textStart\":0,\"textEnd\":6,\"startMs\":25,\"endMs\":410}]"));
        assertEquals("u1", played.utteranceId);
        assertEquals(1, played.words.size());
        PlayedWord hello = played.words.get(0);
        assertEquals("Hello,", hello.text);
        assertEquals(0, hello.textStart);
        assertEquals(6, hello.textEnd);
        assertEquals(25, hello.startMs);
        assertEquals(410, hello.endMs);

        AgentEvent.UtteranceDone done = assertInstanceOf(AgentEvent.UtteranceDone.class,
            parse(8, "\"type\":\"utteranceDone\",\"utteranceId\":\"u1\",\"audioMs\":1200,"
                + "\"atMs\":10200,\"underrunMs\":0"));
        assertEquals("u1", done.utteranceId);
        assertEquals(1200, done.audioMs);
        assertEquals(10200, done.atMs);
        assertEquals(0, done.underrunMs);

        AgentEvent.UtteranceInterrupted cut = assertInstanceOf(AgentEvent.UtteranceInterrupted.class,
            parse(9, "\"type\":\"utteranceInterrupted\",\"utteranceId\":\"u2\","
                + "\"reason\":\"CANCELLED\",\"heardText\":\"Hello,\",\"heardTextEnd\":6,"
                + "\"heardMs\":520"));
        assertEquals("u2", cut.utteranceId);
        assertEquals(AgentEvent.UtteranceInterrupted.CANCELLED, cut.reason);
        assertEquals("Hello,", cut.heardText);
        assertEquals(6, cut.heardTextEnd);
        assertEquals(520, cut.heardMs);
        for (String reason : new String[] {"PREEMPTED", "STREAM_ENDED"}) {
            AgentEvent.UtteranceInterrupted i = assertInstanceOf(AgentEvent.UtteranceInterrupted.class,
                parse(9, "\"type\":\"utteranceInterrupted\",\"utteranceId\":\"u\",\"reason\":\""
                    + reason + "\",\"heardText\":\"\",\"heardTextEnd\":0,\"heardMs\":0"));
            assertEquals(reason, i.reason);
        }
        assertEquals("PREEMPTED", AgentEvent.UtteranceInterrupted.PREEMPTED);
        assertEquals("STREAM_ENDED", AgentEvent.UtteranceInterrupted.STREAM_ENDED);

        AgentEvent.UtteranceFailed failed = assertInstanceOf(AgentEvent.UtteranceFailed.class,
            parse(10, "\"type\":\"utteranceFailed\",\"utteranceId\":\"u3\","
                + "\"code\":\"TTS_CAPACITY\",\"message\":\"full\""));
        assertEquals("u3", failed.utteranceId);
        assertEquals(AgentEvent.UtteranceFailed.TTS_CAPACITY, failed.code);
        assertEquals("full", failed.message);
        assertEquals("TTS_UNAVAILABLE", AgentEvent.UtteranceFailed.TTS_UNAVAILABLE);
        assertEquals("SYNTHESIS_FAILED", AgentEvent.UtteranceFailed.SYNTHESIS_FAILED);
        assertEquals("UNSPEAKABLE_TEXT", AgentEvent.UtteranceFailed.UNSPEAKABLE_TEXT);
    }

    @Test
    void wordOffsetsAreCodePoints() {
        // One emoji is two UTF-16 chars and one code point.
        String text = "Hi \uD83D\uDE00 there, friend.";
        AgentEvent.WordsPlayed played = assertInstanceOf(AgentEvent.WordsPlayed.class,
            parse(7, "\"type\":\"wordsPlayed\",\"utteranceId\":\"u\",\"words\":["
                + "{\"text\":\"there,\",\"textStart\":5,\"textEnd\":11,\"startMs\":0,\"endMs\":1},"
                + "{\"text\":\"friend.\",\"textStart\":12,\"textEnd\":19,\"startMs\":1,\"endMs\":2}]"));
        for (PlayedWord w : played.words) {
            assertEquals(w.text, text.substring(w.charStart(text), w.charEnd(text)), w.toString());
        }
        assertEquals(6, played.words.get(0).charStart(text), "one past the emoji's two chars");
        assertEquals(text.length(), played.words.get(1).charEnd(text));
    }

    @Test
    void speakerCommandsReadAndWriteTheEnginesJson() {
        Gson gson = new Gson();
        JsonObject full = JsonParser.parseString(gson.toJson(Speak.builder()
            .utteranceId("u1").text("Hi.").release(Speak.Release.END)
            .interruptible(false).preemptible(true).voice("en-us-m1").speed(1.25f)
            .build())).getAsJsonObject();
        assertEquals("u1", full.get("utteranceId").getAsString());
        assertEquals("Hi.", full.get("text").getAsString());
        assertEquals("END", full.get("release").getAsString());
        assertFalse(full.get("interruptible").getAsBoolean());
        assertTrue(full.get("preemptible").getAsBoolean());
        assertEquals("en-us-m1", full.get("voice").getAsString());
        assertEquals(1.25f, full.get("speed").getAsFloat(), 1e-6);
        assertEquals(7, full.size(), full.toString());

        assertEquals("{\"utteranceId\":\"u2\",\"text\":\"more\",\"release\":\"NONE\"}",
            gson.toJson(Speak.builder().utteranceId("u2").text("more").build()),
            "unset options are left out");
        assertEquals("FLUSH", JsonParser.parseString(gson.toJson(Speak.builder().utteranceId("u")
            .release(Speak.Release.FLUSH).build())).getAsJsonObject().get("release").getAsString());
        Speak whole = Speak.complete("u3", "Thank you.");
        assertEquals(Speak.Release.END, whole.release);
        assertEquals("Thank you.", whole.text);
        assertThrows(InvalidArgumentException.class, () -> Speak.builder().text("x").build());

        List<CancelledUtterance> cancelled = CancelledUtterance.listFromJson("{\"cancelled\":["
            + "{\"utteranceId\":\"u1\",\"heardText\":\"Hello,\",\"heardMs\":640,\"seq\":12}]}");
        assertEquals(1, cancelled.size());
        assertEquals("u1", cancelled.get(0).utteranceId);
        assertEquals("Hello,", cancelled.get(0).heardText);
        assertEquals(640, cancelled.get(0).heardMs);
        assertEquals(12, cancelled.get(0).seq);
        assertTrue(CancelledUtterance.listFromJson("{\"cancelled\":[]}").isEmpty());
    }

    /** A word as the engine sends it. */
    private static final String HELLO =
        "{\"text\":\"Hello,\",\"startMs\":1200,\"endMs\":1360,\"confidence\":0.5}";

    private static void assertHello(Word w) {
        assertEquals("Hello,", w.text);
        assertEquals(1200, w.startMs);
        assertEquals(1360, w.endMs);
        assertEquals(0.5f, w.confidence, 1e-6);
    }

    /** A turn config as the engine sends it. */
    private static final String CONFIG = "{\"endOfTurnThreshold\":0.5,\"eager\":false,"
        + "\"eagerThreshold\":0.0,\"endOfTurnTimeoutMs\":5000}";

    private static void assertConfig(TurnConfig c) {
        assertNotNull(c);
        assertEquals(0.5f, c.endOfTurnThreshold, 1e-6);
        assertFalse(c.eager);
        assertEquals(0f, c.eagerThreshold, 1e-6);
        assertEquals(5000, c.endOfTurnTimeoutMs);
    }

    @Test
    void readsEagerEndOfTurnAndTurnResumed() {
        AgentEvent.EagerEndOfTurn eager = assertInstanceOf(AgentEvent.EagerEndOfTurn.class, parse(9,
            "\"type\":\"eagerEndOfTurn\",\"turnId\":1,\"text\":\"hello\","
                + "\"words\":[{\"text\":\"hello\",\"startMs\":1200,\"endMs\":1360,\"confidence\":0.5}],"
                + "\"speechEndMs\":2500,\"probability\":0.4,"
                + "\"latency\":{\"sinceSpeechEndMs\":120,\"decisionMs\":30,\"drainMs\":90,\"sttBacklogMs\":80}"));
        assertEquals(1, eager.turnId);
        assertEquals("hello", eager.text);
        assertEquals(1, eager.words.size());
        assertEquals(2500, eager.speechEndMs);
        assertEquals(0.4f, eager.probability, 1e-6);
        assertEquals(120L, eager.sinceSpeechEndMs);
        assertEquals(90L, eager.drainMs);

        AgentEvent.EagerEndOfTurn atSpeechEnd = assertInstanceOf(AgentEvent.EagerEndOfTurn.class,
            parse(10, "\"type\":\"eagerEndOfTurn\",\"turnId\":2,\"text\":\"\",\"words\":[],"
                + "\"speechEndMs\":3000,\"latency\":{}"));
        assertNull(atSpeechEnd.probability, "absent at an eager threshold of 0");
        assertNull(atSpeechEnd.decisionMs);

        AgentEvent.TurnResumed resumed = assertInstanceOf(AgentEvent.TurnResumed.class,
            parse(11, "\"type\":\"turnResumed\",\"turnId\":1,\"cause\":\"SPEECH\",\"atMs\":2700"));
        assertEquals(1, resumed.turnId);
        assertEquals(AgentEvent.TurnResumed.SPEECH, resumed.cause);
        assertEquals(2700, resumed.atMs);
    }

    @Test
    void turnCommandsReadAndWriteTheEnginesJson() {
        AppliedTurnConfig applied = AppliedTurnConfig.fromJson("{\"config\":" + CONFIG + ",\"seq\":9}");
        assertConfig(applied.config);
        assertEquals(9, applied.seq);

        // Updates use the detector config's snake_case keys and leave out unset fields.
        Gson gson = new Gson();
        assertEquals("{}", gson.toJson(TurnConfigUpdate.none()));
        JsonObject update = JsonParser.parseString(gson.toJson(TurnConfigUpdate.none()
            .withEndOfTurnThreshold(0.7f).withEager(false).withEagerThreshold(0.2f)
            .withEndOfTurnTimeoutMs(8000))).getAsJsonObject();
        assertEquals(0.7f, update.get("end_of_turn_threshold").getAsFloat(), 1e-6);
        assertFalse(update.get("eager").getAsBoolean());
        assertEquals(0.2f, update.get("eager_threshold").getAsFloat(), 1e-6);
        assertEquals(8000, update.get("end_of_turn_timeout_ms").getAsInt());

        JsonObject plain = JsonParser.parseString(gson.toJson(new TurnDetectionConfig(16000, 0.5f)))
            .getAsJsonObject();
        assertFalse(plain.has("turns"), "no turns key unless set");
        JsonObject withTurns = JsonParser.parseString(gson.toJson(new TurnDetectionConfig(16000, 0.5f)
            .withTurns(TurnConfigUpdate.none().withEndOfTurnTimeoutMs(0)))).getAsJsonObject();
        assertEquals(0, withTurns.getAsJsonObject("turns").get("end_of_turn_timeout_ms").getAsInt());
        assertEquals(16000, withTurns.get("buffered_samples").getAsInt());
    }

    @Test
    void aKindFromANewerEngineIsUnknownNotAnError() {
        String json = "{" + ENVELOPE + "\"seq\":7,\"type\":\"kindFromTheFuture\",\"turnId\":1}";
        AgentEvent.Unknown u = assertInstanceOf(AgentEvent.Unknown.class, AgentEvent.fromJson(json));
        assertEquals("kindFromTheFuture", u.type);
        assertEquals(json, u.json);
        assertEquals(7, u.seq, "the envelope is still read");
        assertEquals("p", u.participantId);
    }

    @Test
    void optionsCarryTheCursor() {
        AgentStreamOptions d = AgentStreamOptions.defaults();
        assertEquals(-1, d.afterSeq());
        assertEquals(-1, d.streamId());
        assertEquals(0, d.heartbeatMs());

        AgentEvent stopped = parse(9, "\"type\":\"speechStopped\",\"atMs\":1,\"speechMs\":1");
        AgentStreamOptions r = AgentStreamOptions.resumeAfter(stopped).withHeartbeatMs(250);
        assertEquals(9, r.afterSeq());
        assertEquals(4503599627370495L, r.streamId());
        assertEquals(250, r.heartbeatMs());
        assertThrows(InvalidArgumentException.class, () -> AgentStreamOptions.resumeAfter(-1, 0));
        assertThrows(InvalidArgumentException.class, () -> d.withHeartbeatMs(0));
    }

    @Test
    void theExceptionCarriesItsReasonAndCursor() {
        AgentStreamException e = new AgentStreamException(
            AgentStreamException.AGENT_SUBSCRIBER_LAGGED, "dropped after seq 12", 12);
        assertEquals("AGENT_SUBSCRIBER_LAGGED", e.reason());
        assertEquals(12, e.lastSeq());
        assertTrue(e.getMessage().contains("dropped"));
    }
}
