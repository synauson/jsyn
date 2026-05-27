package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.DtmfEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.SipParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.9b — DTMF API contract for a SIP participant.
 *
 * <p>Architecture note: {@link com.synauson.jsyn.event.DtmfEvent} fires when DTMF digits
 * arrive FROM the remote peer via the incoming RTP/RFC 4733 stream.
 * Calling {@link SipParticipantHandle#sendDtmf} sends digits OUT to the remote peer;
 * it does not generate a local DtmfEvent. An event-arrival test requires a real
 * RFC 4733 RTP loopback sender (out of scope for the unit test suite).
 *
 * <p>This test verifies:
 * <ol>
 *   <li>DTMF event subscription opens without error.</li>
 *   <li>All 16 valid DTMF characters (0-9, *, #, A-D) are accepted by sendDtmf().</li>
 *   <li>Invalid characters are rejected with IllegalArgumentException.</li>
 *   <li>The observer is never called during sends (no phantom events).</li>
 * </ol>
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class DtmfEventsIT {

    @Test
    void dtmfSubscriptionOpensAndSendDigitsAccepted() throws Exception {
        String confId = "dtmf-it-" + System.nanoTime();

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle sip = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId("dtmf-sip")
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(19300)
                            .codec("PCMU")
                            .dtmfPayloadType(101)
                            .build());

            AtomicInteger observerCallCount = new AtomicInteger(0);

            try (Subscription sub = conf.streamDtmfEvents("dtmf-sip", ev -> {
                // Should not be called during these sends (no RTP loopback peer)
                observerCallCount.incrementAndGet();
            })) {
                // All 16 valid DTMF characters must be accepted.
                char[] validDigits = {'0','1','2','3','4','5','6','7','8','9','*','#','A','B','C','D'};
                for (char digit : validDigits) {
                    assertDoesNotThrow(() -> sip.sendDtmf(digit, 100),
                            "sendDtmf('" + digit + "') should not throw");
                }

                // Invalid characters must be rejected.
                assertThrows(IllegalArgumentException.class, () -> sip.sendDtmf('X', 100),
                        "sendDtmf('X') should throw IllegalArgumentException");
                assertThrows(IllegalArgumentException.class, () -> sip.sendDtmf('Z', 100),
                        "sendDtmf('Z') should throw IllegalArgumentException");
            }

            assertEquals(0, observerCallCount.get(),
                    "observer should not receive phantom DTMF events without an RTP loopback");

            conf.removeParticipant("dtmf-sip");
        }
    }
}
