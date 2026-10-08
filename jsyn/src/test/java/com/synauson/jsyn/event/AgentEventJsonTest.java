package com.synauson.jsyn.event;

import com.synauson.jsyn.AgentStreamOptions;
import com.synauson.jsyn.exception.AgentStreamException;
import com.synauson.jsyn.exception.InvalidArgumentException;
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
            parse(0, "\"type\":\"subscribed\",\"oldestSeq\":1,\"lastSeq\":4,\"stt\":true"));
        assertEquals(1, sub.oldestSeq);
        assertEquals(4, sub.lastSeq);
        assertTrue(sub.stt);
        assertFalse(sub.isStored());

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
        assertEquals("STT_LAGGING", err.reason);
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
    }

    @Test
    void aKindFromANewerEngineIsUnknownNotAnError() {
        String json = "{" + ENVELOPE + "\"seq\":7,\"type\":\"turnStarted\",\"turnId\":1}";
        AgentEvent.Unknown u = assertInstanceOf(AgentEvent.Unknown.class, AgentEvent.fromJson(json));
        assertEquals("turnStarted", u.type);
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
