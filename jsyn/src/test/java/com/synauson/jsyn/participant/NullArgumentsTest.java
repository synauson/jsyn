package com.synauson.jsyn.participant;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.spec.PriorityFile;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Null arguments to the public API surface as InvalidArgumentException naming the argument,
 * before any native call. None of these touch the native library: every check runs first.
 */
class NullArgumentsTest {

    // Handle 0 is never issued by the native handle map. The conference is deliberately
    // left open: close() would call into the native library, which these tests don't load.
    private final Conference conf = new Conference(0L, "null-args-conf");

    private static void assertInvalid(String expectedMessage, Executable call) {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class, call);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    void jsynRejectsNullConfig() {
        assertInvalid("config must not be null", () -> new JSyn(null));
    }

    @Test
    void conferenceRejectsNullId() {
        assertInvalid("conferenceId must not be null", () -> new Conference(0L, null));
    }

    @Test
    void conferenceRejectsNullSpecs() {
        assertInvalid("spec must not be null", () -> conf.addFileParticipant(null));
        assertInvalid("spec must not be null", () -> conf.addRecordingParticipant(null));
        assertInvalid("spec must not be null", () -> conf.addSipParticipant(null));
        assertInvalid("spec must not be null", () -> conf.reserveSipParticipant(null));
        assertInvalid("spec must not be null", () -> conf.connectSipParticipant(null));
        assertInvalid("spec must not be null", () -> conf.addWebRtcParticipant(null));
        assertInvalid("spec must not be null", () -> conf.addNativeParticipant("n", null));
        assertInvalid("matrix must not be null", () -> conf.updatePartyAudioConnections(null));
    }

    @Test
    void conferenceRejectsNullParticipantIds() {
        assertInvalid("participantId must not be null", () -> conf.addNativeParticipant(null, null));
        assertInvalid("participantId must not be null", () -> conf.removeParticipant(null));
        assertInvalid("participantId must not be null", () -> conf.muteParticipant(null, true));
        assertInvalid("participantId must not be null",
            () -> conf.addPriorityAudioFiles(null, Arrays.asList(new PriorityFile("file:///a.wav"))));
        assertInvalid("participantId must not be null", () -> conf.streamVadEvents(null, e -> {}));
        assertInvalid("participantId must not be null", () -> conf.streamSmartTurnEvents(null, e -> {}));
        assertInvalid("participantId must not be null", () -> conf.streamFileEvents(null, e -> {}));
        assertInvalid("participantId must not be null", () -> conf.streamDtmfEvents(null, e -> {}));
        assertInvalid("participantId must not be null",
            () -> conf.streamWebRtcIceCandidates(null, e -> {}));
    }

    @Test
    void conferenceRejectsNullFilesAndObservers() {
        assertInvalid("files must not be null", () -> conf.addPriorityAudioFiles("p", null));
        assertInvalid("files[1] must not be null", () -> conf.addPriorityAudioFiles("p",
            Arrays.asList(new PriorityFile("file:///a.wav"), null)));
        assertInvalid("observer must not be null", () -> conf.streamVadEvents("p", null));
        assertInvalid("observer must not be null", () -> conf.streamSmartTurnEvents("p", null));
        assertInvalid("observer must not be null", () -> conf.streamFileEvents("p", null));
        assertInvalid("observer must not be null", () -> conf.streamDtmfEvents("p", null));
        assertInvalid("observer must not be null", () -> conf.streamWebRtcIceCandidates("p", null));
    }

    @Test
    void handlesRejectNullIds() {
        assertInvalid("participantId must not be null", () -> new FileParticipantHandle(null));
        assertInvalid("participantId must not be null", () -> new RecordingParticipantHandle(null));
        assertInvalid("participantId must not be null", () -> new SipReservation(null, 10000, 10001));
        assertInvalid("conferenceId must not be null",
            () -> new SipParticipantHandle(0L, null, "p", 10000));
        assertInvalid("participantId must not be null",
            () -> new SipParticipantHandle(0L, "c", null, 10000));
        assertInvalid("sdpAnswer must not be null",
            () -> new WebRtcParticipantHandle(0L, "c", "p", null));
        assertInvalid("nativeHandle must not be null", () -> new NativeParticipant("c", "p", null));
    }

    @Test
    void webRtcHandleRejectsNullCandidate() {
        WebRtcParticipantHandle h = new WebRtcParticipantHandle(0L, "c", "p", "v=0");
        assertInvalid("candidate must not be null", () -> h.addIceCandidate(null, 0));
    }
}
