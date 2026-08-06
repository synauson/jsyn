package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.VadEvent;
import com.synauson.jsyn.it.support.WebRtcBrowserPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.WebRtcParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.VadConfig;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep WebRTC media test: a real headless Chromium browser negotiates real
 * ICE/DTLS-SRTP/Opus with synauson and plays real speech as its fake
 * microphone, proving VAD fires through the actual WebRTC transport —
 * something {@link WebRtcParticipantIT}'s canned-SDP signaling-only test
 * never exercises.
 *
 * <p>Sequencing note: {@code addWebRtcParticipant} deliberately does not
 * block on the participant becoming connected (see the "Do NOT block on
 * participant.ready()" comment in {@code ConferenceActor}), because the SDP
 * answer must reach the browser before ICE can complete. Local candidates
 * webrtcbin emits during {@code set-local-description} are buffered by the
 * conference actor and replayed to a late subscriber, so subscribing after
 * {@code applyAnswer} loses nothing.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class WebRtcMediaE2eIT {

    /** Synauson's own webrtcbin STUN URI; host-only candidates suffice locally. */
    private static final String STUN = "stun://stun.l.google.com:19302";

    @Test
    void realSpeechThroughWebRtcTriggersVad() throws Exception {
        Path speechWav = JSynTestHelpers.resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures/short_speech.wav");
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav not found — skipping WebRTC media IT");
        Path modelsDir = JSynTestHelpers.resolveSynausonRepo().resolve("models");
        Assumptions.assumeTrue(modelsDir.resolve("silero_vad.onnx").toFile().exists(),
                "silero_vad.onnx not found — skipping WebRTC media IT");

        long ts = System.nanoTime();
        String confId = "webrtc-media-it-" + ts;
        String pid = "webrtc-media-p-" + ts;

        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer(speechWav.toString());
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            String offer = browser.createOffer();
            assertTrue(offer.startsWith("v=0"), "browser must produce a real SDP offer");

            WebRtcParticipantHandle handle = conf.addWebRtcParticipant(
                    WebRtcParticipantSpec.builder()
                            .participantId(pid)
                            .sdpOffer(offer)
                            .stunServer(STUN)
                            .jitterBufferMs(200)
                            .vad(VadConfig.defaults())
                            .build());
            browser.applyAnswer(handle.sdpAnswer());

            CountDownLatch speechStart = new CountDownLatch(1);
            try (Subscription vadSub = conf.streamVadEvents(pid, ev -> {
                if (ev instanceof VadEvent.SpeechStart) {
                    speechStart.countDown();
                }
            });
                 Subscription iceSub = conf.streamWebRtcIceCandidates(pid, ev -> {
                     if (!ev.endOfCandidates) {
                         browser.addRemoteIceCandidate(ev.candidate, ev.sdpMLineIndex);
                     }
                 })) {

                long iceDeadline = System.currentTimeMillis() + 60_000;
                while (System.currentTimeMillis() < iceDeadline && speechStart.getCount() > 0) {
                    for (Map<String, Object> candidate : browser.drainLocalIceCandidates()) {
                        handle.addIceCandidate(
                                (String) candidate.get("candidate"),
                                ((Number) candidate.get("sdpMLineIndex")).intValue());
                    }
                    if (speechStart.await(500, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }

                assertTrue(speechStart.getCount() == 0,
                        "SpeechStart did not arrive through the real WebRTC media path");
            }

            conf.removeParticipant(pid);
        }
    }

    @Test
    void audioFlowsBackToTheBrowser() throws Exception {
        long ts = System.nanoTime();
        String confId = "webrtc-echo-it-" + ts;
        String pid = "webrtc-echo-p-" + ts;

        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer(); // default synthetic fake-mic tone
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            String offer = browser.createOffer();
            WebRtcParticipantHandle handle = conf.addWebRtcParticipant(
                    WebRtcParticipantSpec.builder()
                            .participantId(pid)
                            .sdpOffer(offer)
                            .stunServer(STUN)
                            .jitterBufferMs(200)
                            .build());
            browser.applyAnswer(handle.sdpAnswer());

            // Self-route so synauson sends the browser's own audio straight back.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));

            try (Subscription iceSub = conf.streamWebRtcIceCandidates(pid, ev -> {
                if (!ev.endOfCandidates) {
                    browser.addRemoteIceCandidate(ev.candidate, ev.sdpMLineIndex);
                }
            })) {
                long iceDeadline = System.currentTimeMillis() + 10_000;
                while (System.currentTimeMillis() < iceDeadline) {
                    for (Map<String, Object> candidate : browser.drainLocalIceCandidates()) {
                        handle.addIceCandidate(
                                (String) candidate.get("candidate"),
                                ((Number) candidate.get("sdpMLineIndex")).intValue());
                    }
                    Thread.sleep(500);
                }

                assertTrue(browser.receivedNonSilentAudio(Duration.ofSeconds(30)),
                        "browser did not observe non-silent audio echoed back over WebRTC");
            }

            conf.removeParticipant(pid);
        }
    }
}
