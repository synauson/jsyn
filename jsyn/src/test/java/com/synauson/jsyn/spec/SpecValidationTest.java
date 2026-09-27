package com.synauson.jsyn.spec;

import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.exception.InvalidArgumentException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

/** Null arguments and missing builder fields surface as InvalidArgumentException. */
class SpecValidationTest {

    private static void assertInvalid(String expectedMessage, Executable call) {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class, call);
        assertEquals(expectedMessage, e.getMessage());
    }

    // --- builders report every missing required field at once ---------------

    @Test
    void fileParticipantSpecReportsMissingFields() {
        assertInvalid("FileParticipantSpec requires id, uri",
            () -> FileParticipantSpec.builder().build());
        assertInvalid("FileParticipantSpec requires uri",
            () -> FileParticipantSpec.builder().id("a").build());
    }

    @Test
    void recordingParticipantSpecReportsMissingFields() {
        assertInvalid("RecordingParticipantSpec requires id, sourceParticipantId, outputPath",
            () -> RecordingParticipantSpec.builder().build());
        assertInvalid("RecordingParticipantSpec requires outputPath",
            () -> RecordingParticipantSpec.builder().id("r").sourceParticipantId("a").build());
    }

    @Test
    void nativeParticipantSpecReportsMissingFormat() {
        assertInvalid("NativeParticipantSpec requires format",
            () -> NativeParticipantSpec.builder().build());
    }

    @Test
    void sipParticipantSpecReportsMissingFields() {
        assertInvalid("SipParticipantSpec requires participantId, remoteIp, codec",
            () -> SipParticipantSpec.builder().remoteRtpPort(4000).build());
        assertInvalid("SipParticipantSpec requires remoteIp",
            () -> SipParticipantSpec.builder().participantId("s").codec("PCMU").build());
    }

    @Test
    void sipReservationSpecReportsMissingParticipantId() {
        assertInvalid("SipReservationSpec requires participantId",
            () -> SipReservationSpec.builder().ourSrtpKey(new byte[30]).build());
    }

    @Test
    void sipRemoteMediaReportsMissingFields() {
        assertInvalid("SipRemoteMedia requires remoteIp, codec",
            () -> SipRemoteMedia.builder().remoteRtpPort(4000).build());
        assertInvalid("SipRemoteMedia requires codec",
            () -> SipRemoteMedia.builder().remoteIp("127.0.0.1").build());
    }

    @Test
    void sipConnectionSpecReportsMissingFields() {
        assertInvalid("SipConnectionSpec requires participantId, remote",
            () -> SipConnectionSpec.builder().build());
    }

    @Test
    void webRtcParticipantSpecReportsMissingFields() {
        assertInvalid("WebRtcParticipantSpec requires participantId, sdpOffer, stunServer",
            () -> WebRtcParticipantSpec.builder().build());
        assertInvalid("WebRtcParticipantSpec requires stunServer",
            () -> WebRtcParticipantSpec.builder().participantId("w").sdpOffer("v=0").build());
    }

    // --- optional fields stay optional ---------------------------------------

    @Test
    void optionalFieldsAcceptNull() {
        FileParticipantSpec file = FileParticipantSpec.builder()
            .id("a").uri("file:///a.wav").vad(null).smartTurn(null).build();
        assertNull(file.vad);
        assertNull(file.smartTurn);

        SipParticipantSpec sip = SipParticipantSpec.builder()
            .participantId("s").remoteIp("127.0.0.1").remoteRtpPort(4000).codec("PCMU")
            .srtp(null).vad(null).smartTurn(null).build();
        assertNull(sip.srtp);

        SipRemoteMedia remote = SipRemoteMedia.builder()
            .remoteIp("127.0.0.1").remoteRtpPort(4000).codec("PCMU").srtpKey(null).build();
        assertNull(remote.srtpKey);

        SipReservationSpec reservation = SipReservationSpec.builder()
            .participantId("s").ourSrtpKey(null).build();
        assertNull(reservation.ourSrtpKey);

        NativeParticipantSpec nat = NativeParticipantSpec.builder()
            .format(NativeAudioFormat.PCM_S16LE16K_MONO).vad(null).smartTurn(null).build();
        assertNull(nat.vad);
    }

    // --- constructors and factories ------------------------------------------

    @Test
    void connectionEntryRejectsNullIds() {
        assertInvalid("sourceId must not be null", () -> new ConnectionEntry(null, "b", false));
        assertInvalid("destId must not be null", () -> ConnectionEntry.connect("a", null));
        assertInvalid("destId must not be null", () -> ConnectionEntry.muted("a", null));
    }

    @Test
    void connectionMatrixRejectsNullListArrayAndElements() {
        assertInvalid("entries must not be null", () -> new ConnectionMatrix((List<ConnectionEntry>) null));
        assertInvalid("entries must not be null", () -> new ConnectionMatrix((ConnectionEntry[]) null));
        assertInvalid("entries[1] must not be null", () -> new ConnectionMatrix(
            Arrays.asList(ConnectionEntry.connect("a", "b"), null)));
        assertInvalid("entries[0] must not be null",
            () -> new ConnectionMatrix((ConnectionEntry) null));
    }

    @Test
    void connectionMatrixCopiesVarargs() {
        ConnectionEntry[] entries = { ConnectionEntry.connect("a", "b") };
        ConnectionMatrix m = new ConnectionMatrix(entries);
        entries[0] = ConnectionEntry.connect("x", "y");
        assertEquals("a", m.entries.get(0).sourceId);
    }

    @Test
    void priorityFileRejectsNullUri() {
        assertInvalid("uri must not be null", () -> new PriorityFile(null));
    }

    @Test
    void srtpConfigRejectsNullKeys() {
        assertInvalid("ourKey must not be null", () -> new SrtpConfig(null, new byte[30]));
        assertInvalid("theirKey must not be null", () -> new SrtpConfig(new byte[30], null));
    }
}
