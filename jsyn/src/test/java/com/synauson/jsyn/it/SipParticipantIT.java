package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.SipStats;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.SipParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.8 — SIP participant pipeline lifecycle.
 *
 * <p>Does not require real RTP traffic. Verifies:
 * <ul>
 *   <li>addSipParticipant returns a handle with a valid even RTP port.</li>
 *   <li>stats() does not throw.</li>
 *   <li>removeParticipant succeeds without error.</li>
 * </ul>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SipParticipantIT {

    @Test
    void addSipParticipantAllocatesValidPort() throws Exception {
        String confId = "sip-it-" + System.nanoTime();

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId("sip-p1")
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(19100)
                            .codec("PCMU")
                            .dtmfPayloadType(0)
                            .build());

            assertNotNull(handle, "handle must not be null");
            assertEquals("sip-p1", handle.id());
            int port = handle.localRtpPort();
            assertTrue(port > 0, "localRtpPort must be positive, was " + port);
            assertEquals(0, port % 2, "RTP port must be even per RFC 3550");

            // stats() should not throw even without real RTP traffic
            SipStats stats = handle.stats();
            assertNotNull(stats);

            conf.removeParticipant("sip-p1");
        }
    }

    @Test
    void multipleSipParticipantsGetDistinctPorts() throws Exception {
        String confId = "sip-ports-it-" + System.nanoTime();

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle h1 = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId("sip-mp1")
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(19200)
                            .codec("PCMU")
                            .dtmfPayloadType(0)
                            .build());

            SipParticipantHandle h2 = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId("sip-mp2")
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(19202)
                            .codec("PCMA")
                            .dtmfPayloadType(0)
                            .build());

            assertNotEquals(h1.localRtpPort(), h2.localRtpPort(),
                    "two participants must receive distinct RTP ports");

            conf.removeParticipant("sip-mp1");
            conf.removeParticipant("sip-mp2");
        }
    }
}
