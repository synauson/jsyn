package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.DtmfEvent;
import com.synauson.jsyn.it.support.RtpPacket;
import com.synauson.jsyn.it.support.SipRtpPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.SipParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep bidirectional DTMF test using real RFC 4733 RTP packets via
 * {@link com.synauson.jsyn.it.support.SipRtpPeer}.
 *
 * <p>Closes the gap {@link DtmfEventsIT}'s own docstring flags as out of
 * scope: "an event-arrival test requires a real RFC 4733 RTP loopback
 * sender." {@link SipRtpPeer} is that loopback sender.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SipDtmfE2eIT {

    private static final int DTMF_PAYLOAD_TYPE = 101;

    @Test
    void sendDtmfProducesRealRfc4733WireFrame() throws Exception {
        long ts = System.nanoTime();
        String confId = "dtmf-send-it-" + ts;
        String pid = "dtmf-send-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(pid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(DTMF_PAYLOAD_TYPE)
                            .build());
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            handle.sendDtmf('5', 150);

            long deadline = System.currentTimeMillis() + 5000;
            boolean found = false;
            while (System.currentTimeMillis() < deadline && !found) {
                for (RtpPacket p : peer.capturedPackets()) {
                    if (p.payloadType == DTMF_PAYLOAD_TYPE && p.payload.length >= 1
                            && (p.payload[0] & 0xFF) == 5) {
                        found = true;
                        break;
                    }
                }
                if (!found) Thread.sleep(50);
            }
            assertTrue(found, "expected a real RFC 4733 wire packet for digit '5' (event 5)");

            conf.removeParticipant(pid);
        }
    }

    @Test
    void remoteDtmfEventFiresOverRfc4733() throws Exception {
        long ts = System.nanoTime();
        String confId = "dtmf-recv-it-" + ts;
        String pid = "dtmf-recv-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(pid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(DTMF_PAYLOAD_TYPE)
                            .build());
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            CountDownLatch gotEvent = new CountDownLatch(1);
            AtomicReference<DtmfEvent> received = new AtomicReference<>();
            try (Subscription sub = conf.streamDtmfEvents(pid, ev -> {
                received.set(ev);
                gotEvent.countDown();
            })) {
                peer.sendDtmfEvent(9, 150, DTMF_PAYLOAD_TYPE);

                assertTrue(gotEvent.await(10, TimeUnit.SECONDS),
                        "expected a DtmfEvent from a real inbound RFC 4733 sender");
                assertEquals("9", received.get().digit);
                // synauson-core classifies DTMF by transport, not colloquial usage:
                // RFC 4733 telephone-event packets are "out-of-band" relative to the
                // voice codec (a separate RTP payload type carrying digit metadata,
                // not audio samples), while dtmfdetect's audio-domain tone detection
                // is "in-band". See ConferenceActor::handle_dtmf_bus_message's doc
                // comment in synauson-core/src/conference/actor.rs.
                assertFalse(received.get().inBand,
                        "RFC 4733 telephone-event digits are out-of-band, not in-band");
            }

            conf.removeParticipant(pid);
        }
    }
}
