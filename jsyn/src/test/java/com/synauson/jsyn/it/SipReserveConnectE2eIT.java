package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.exception.AlreadyExistsException;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.exception.NotFoundException;
import com.synauson.jsyn.it.support.SipRtpPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.participant.SipReservation;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.SipConnectionSpec;
import com.synauson.jsyn.spec.SipRemoteMedia;
import com.synauson.jsyn.spec.SipReservationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.synauson.jsyn.it.SipMixedSourcesE2eIT.describe;
import static com.synauson.jsyn.it.SipMixedSourcesE2eIT.sipSpec;
import static com.synauson.jsyn.it.support.ToneAnalysis.awaitJointTone;
import static com.synauson.jsyn.it.support.ToneAnalysis.pcmuSamples;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-phase outbound SIP add, over real UDP in real time: reserve the local ports for our
 * SDP offer, then connect once the peer's answer names its media.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SipReserveConnectE2eIT {

    private static final double CALLEE_TONE_HZ = 440.0;
    private static final double LISTENER_TONE_HZ = 1000.0;
    private static final double PRESENT = 1000.0;
    private static final int WINDOW_8K = 1600;
    private static final Duration HEAR_WITHIN = Duration.ofSeconds(10);

    /**
     * The outbound call end to end. The reserved port is known before anything runs, the
     * callee's early media is already arriving on it when the answer is applied, and once
     * connected audio flows both ways, leaving from the port it arrives on (symmetric RTP).
     */
    @Test
    void reservedPortTakesEarlyMediaThenCarriesAudioBothWays() throws Exception {
        long ts = System.nanoTime();
        String callee = "reserve-callee-" + ts;
        String listener = "reserve-listener-" + ts;
        int rtpMin = JSynTestHelpers.nextRtpPortMin();

        try (SipRtpPeer calleePeer = new SipRtpPeer();
             SipRtpPeer listenerPeer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn(rtpMin);
             Conference conf = syn.startConference("reserve-conf-" + ts)) {

            SipParticipantHandle listenerHandle = conf.addSipParticipant(sipSpec(listener, listenerPeer, 101));
            listenerPeer.setTarget("127.0.0.1", listenerHandle.localRtpPort());
            listenerPeer.startTone(LISTENER_TONE_HZ);

            // Phase one: the port for our offer's m= line.
            SipReservation reservation = conf.reserveSipParticipant(
                    SipReservationSpec.builder().participantId(callee).build());
            int port = reservation.localRtpPort();
            assertEquals(callee, reservation.id());
            assertEquals(0, port % 2, "RTP port " + port + " must be even");
            assertEquals(port + 1, reservation.localRtcpPort());
            assertTrue(port >= rtpMin && port < rtpMin + 200,
                    "reserved port " + port + " outside this JSyn's range starting at " + rtpMin);
            assertFalse(participantIds(conf).contains(callee), "a reservation is not a participant");

            // The callee answers and starts sending before we have applied its answer.
            calleePeer.setTarget("127.0.0.1", port);
            calleePeer.startTone(CALLEE_TONE_HZ);
            Thread.sleep(300);

            // Phase two: the answer's media.
            SipParticipantHandle handle = conf.connectSipParticipant(SipConnectionSpec.builder()
                    .participantId(callee)
                    .remote(SipRemoteMedia.builder()
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(calleePeer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(101)
                            .build())
                    .build());
            assertEquals(callee, handle.id());
            assertEquals(port, handle.localRtpPort(), "connect must use the reserved port");
            assertTrue(participantIds(conf).contains(callee));

            conf.updatePartyAudioConnections(new ConnectionMatrix(
                    ConnectionEntry.connect(callee, listener),
                    ConnectionEntry.connect(listener, callee)));
            calleePeer.clearCaptured();
            listenerPeer.clearCaptured();

            double heard = awaitJointTone(() -> pcmuSamples(listenerPeer.capturedPackets()),
                    8000, WINDOW_8K, PRESENT, HEAR_WITHIN, CALLEE_TONE_HZ);
            double hears = awaitJointTone(() -> pcmuSamples(calleePeer.capturedPackets()),
                    8000, WINDOW_8K, PRESENT, HEAR_WITHIN, LISTENER_TONE_HZ);
            assertTrue(heard >= PRESENT, "callee->listener 440 Hz peak " + (int) heard
                    + ", callee packetsReceived " + handle.stats().packetsReceived
                    + "; listener peer " + describe(listenerPeer.capturedPackets()));
            assertTrue(hears >= PRESENT, "listener->callee 1 kHz peak " + (int) hears
                    + "; callee peer " + describe(calleePeer.capturedPackets()));
            assertEquals(Set.of(port), calleePeer.capturedSourcePorts(),
                    "our RTP to the callee must leave from the reserved port");

            conf.updatePartyAudioConnections(ConnectionMatrix.empty());
            conf.removeParticipant(callee);
            conf.removeParticipant(listener);
        }
    }

    /**
     * A reservation's rules seen from Java: connect needs one and connects it once, an id is
     * reserved once, SRTP keys pair across the phases (with key bytes above 0x7f, which Gson
     * writes as negative numbers), and removal or termination returns the ports.
     */
    @Test
    void reservationRulesAndReleaseSurfaceAsExceptions() throws Exception {
        long ts = System.nanoTime();
        int rtpMin = JSynTestHelpers.nextRtpPortMin();
        byte[] ourKey = new byte[30];
        byte[] theirKey = new byte[30];
        SecureRandom random = new SecureRandom();
        random.nextBytes(ourKey);
        random.nextBytes(theirKey);
        ourKey[0] = (byte) 0xff;
        theirKey[0] = (byte) 0x80;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn(rtpMin)) {
            SipRemoteMedia plain = remote(peer, null);
            int firstPort;

            try (Conference conf = syn.startConference("reserve-rules-" + ts)) {
                assertThrows(NotFoundException.class, () -> conf.connectSipParticipant(
                        SipConnectionSpec.builder().participantId("ghost").remote(plain).build()));

                SipReservation a = conf.reserveSipParticipant(
                        SipReservationSpec.builder().participantId("a").build());
                firstPort = a.localRtpPort();
                assertThrows(AlreadyExistsException.class, () -> conf.reserveSipParticipant(
                        SipReservationSpec.builder().participantId("a").build()));

                // A refused connect keeps the reservation; a good one uses it, once.
                assertThrows(InvalidArgumentException.class, () -> conf.connectSipParticipant(
                        SipConnectionSpec.builder().participantId("a")
                                .remote(remote(peer, theirKey)).build()));
                SipParticipantHandle handle = conf.connectSipParticipant(
                        SipConnectionSpec.builder().participantId("a").remote(plain).build());
                assertEquals(firstPort, handle.localRtpPort());
                assertThrows(AlreadyExistsException.class, () -> conf.connectSipParticipant(
                        SipConnectionSpec.builder().participantId("a").remote(plain).build()));

                // SRTP: our key at reservation, the peer's at connect.
                assertThrows(InvalidArgumentException.class, () -> conf.reserveSipParticipant(
                        SipReservationSpec.builder().participantId("short")
                                .ourSrtpKey(new byte[29]).build()));
                SipReservation secure = conf.reserveSipParticipant(
                        SipReservationSpec.builder().participantId("secure").ourSrtpKey(ourKey).build());
                assertThrows(InvalidArgumentException.class, () -> conf.connectSipParticipant(
                        SipConnectionSpec.builder().participantId("secure").remote(plain).build()));
                SipParticipantHandle secureHandle = conf.connectSipParticipant(
                        SipConnectionSpec.builder().participantId("secure")
                                .remote(remote(peer, theirKey)).build());
                assertEquals(secure.localRtpPort(), secureHandle.localRtpPort());
                assertTrue(participantIds(conf).containsAll(List.of("a", "secure")));

                // Removing an unconnected reservation returns its port for the next one.
                SipReservation spare = conf.reserveSipParticipant(
                        SipReservationSpec.builder().participantId("spare").build());
                conf.removeParticipant("spare");
                assertThrows(NotFoundException.class, () -> conf.connectSipParticipant(
                        SipConnectionSpec.builder().participantId("spare").remote(plain).build()));
                SipReservation again = conf.reserveSipParticipant(
                        SipReservationSpec.builder().participantId("spare-2").build());
                assertEquals(spare.localRtpPort(), again.localRtpPort(),
                        "a removed reservation's port must be reusable");

                conf.removeParticipant("a");
                conf.removeParticipant("secure");
                // "spare-2" is still reserved when the conference terminates.
            }

            // Termination released every port, so a new conference starts from the bottom again.
            try (Conference conf = syn.startConference("reserve-rules-after-" + ts)) {
                SipReservation fresh = conf.reserveSipParticipant(
                        SipReservationSpec.builder().participantId("fresh").build());
                assertEquals(firstPort, fresh.localRtpPort(),
                        "terminating a conference must release its reservations");
                conf.removeParticipant("fresh");
            }
        }
    }

    private static SipRemoteMedia remote(SipRtpPeer peer, byte[] srtpKey) {
        return SipRemoteMedia.builder()
                .remoteIp("127.0.0.1")
                .remoteRtpPort(peer.localPort())
                .codec("PCMU")
                .dtmfPayloadType(101)
                .srtpKey(srtpKey)
                .build();
    }

    private static List<String> participantIds(Conference conf) {
        return conf.state().participants.stream()
                .map(p -> p.participantId)
                .collect(Collectors.toList());
    }
}
