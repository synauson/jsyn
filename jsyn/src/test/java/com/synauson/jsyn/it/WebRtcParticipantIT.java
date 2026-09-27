package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.WebRtcParticipantHandle;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.9 — WebRTC participant SDP exchange.
 *
 * <p>Uses a minimal but well-formed SDP offer (audio-only, Opus, PT=111)
 * that GStreamer's webrtcbin can parse and respond to without an ICE agent.
 * Verifies the handle carries a non-null, non-empty SDP answer starting with
 * {@code "v=0"}.
 *
 * <p>Note: a full ICE + media exchange requires a second GStreamer peer;
 * this test only exercises the SDP signaling half.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WebRtcParticipantIT {

    /**
     * Minimal audio-only SDP offer (Opus PT=111) that GStreamer webrtcbin
     * accepts. Matches the format produced by a real browser/FakeAgent.
     */
    private static final String MINIMAL_OPUS_OFFER = String.join("\r\n",
            "v=0",
            "o=- 123456789 2 IN IP4 127.0.0.1",
            "s=-",
            "t=0 0",
            "a=group:BUNDLE audio",
            "a=msid-semantic: WMS stream1",
            "m=audio 9 UDP/TLS/RTP/SAVPF 111",
            "c=IN IP4 0.0.0.0",
            "a=rtcp:9 IN IP4 0.0.0.0",
            "a=ice-ufrag:testufrag",
            "a=ice-pwd:testpassword123456789012",
            "a=fingerprint:sha-256 AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:" +
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
            "a=setup:actpass",
            "a=mid:audio",
            "a=sendrecv",
            "a=msid:stream1 track1",
            "a=rtcp-mux",
            "a=rtpmap:111 opus/48000/2",
            "a=fmtp:111 minptime=10;useinbandfec=1",
            "a=ssrc:100000001 cname:test",
            "a=ssrc:100000001 msid:stream1 track1",
            "" // trailing CRLF
    );

    @Test
    void sdpExchangeProducesAnswer() throws Exception {
        String confId = "webrtc-it-" + System.nanoTime();

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            WebRtcParticipantHandle handle = conf.addWebRtcParticipant(
                    WebRtcParticipantSpec.builder()
                            .participantId("webrtc-p1")
                            .sdpOffer(MINIMAL_OPUS_OFFER)
                            .stunServer("stun://stun.l.google.com:19302")
                            .jitterBufferMs(200)
                            .build());

            assertNotNull(handle, "handle must not be null");
            assertEquals("webrtc-p1", handle.id());

            String sdpAnswer = handle.sdpAnswer();
            assertNotNull(sdpAnswer, "SDP answer must not be null");
            assertFalse(sdpAnswer.isBlank(), "SDP answer must not be blank");
            assertTrue(sdpAnswer.startsWith("v=0"),
                    "SDP answer must start with 'v=0', got: " + sdpAnswer.substring(0, Math.min(20, sdpAnswer.length())));

            conf.removeParticipant("webrtc-p1");
        }
    }
}
