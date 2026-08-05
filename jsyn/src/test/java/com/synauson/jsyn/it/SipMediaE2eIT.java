package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.VadEvent;
import com.synauson.jsyn.it.support.SipRtpPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.SipParticipantSpec;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep SIP media test using {@link com.synauson.jsyn.it.support.SipRtpPeer}: real inbound RTP/PCMU audio
 * triggers VAD on the SIP path, and real outbound RTP/PCMU audio is
 * captured actually leaving the wire.
 *
 * <p>Unlike {@link SipParticipantIT} (port allocation and handle lifecycle
 * only — "does not require real RTP traffic" per its own docstring), this
 * pushes actual RTP packets over a real UDP socket in both directions.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SipMediaE2eIT {

    @Test
    void inboundAudioTriggersVadOverSipPath() throws Exception {
        Path speechWav = JSynTestHelpers.resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures/short_speech.wav");
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav not found — skipping SIP media IT");
        Path modelsDir = JSynTestHelpers.resolveSynausonRepo().resolve("models");
        Assumptions.assumeTrue(modelsDir.resolve("silero_vad.onnx").toFile().exists(),
                "silero_vad.onnx not found — skipping SIP media IT");

        byte[] pcm16k = JSynTestHelpers.readPcmFromWav(speechWav);
        byte[] pcm8k = downsampleS16LE16kTo8k(pcm16k);

        long ts = System.nanoTime();
        String confId = "sip-media-it-" + ts;
        String pid = "sip-media-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(pid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(101)
                            .vad(VadConfig.defaults())
                            .build());
            assertNotNull(handle);
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            CountDownLatch speechStart = new CountDownLatch(1);
            try (Subscription sub = conf.streamVadEvents(pid, ev -> {
                if (ev instanceof VadEvent.SpeechStart) {
                    speechStart.countDown();
                }
            })) {
                int frameBytes = 320;
                for (int repeat = 0; repeat < 3 && speechStart.getCount() > 0; repeat++) {
                    int offset = 0;
                    while (offset + frameBytes <= pcm8k.length && speechStart.getCount() > 0) {
                        byte[] frame = new byte[frameBytes];
                        System.arraycopy(pcm8k, offset, frame, 0, frameBytes);
                        peer.sendAudioFrame(frame);
                        offset += frameBytes;
                        Thread.sleep(18);
                    }
                }
                assertTrue(speechStart.await(10, TimeUnit.SECONDS),
                        "SpeechStart did not arrive from real inbound SIP RTP audio");
            }

            conf.removeParticipant(pid);
        }
    }

    @Test
    void outboundAudioLeavesTheWire() throws Exception {
        long ts = System.nanoTime();
        String confId = "sip-out-it-" + ts;
        String sipPid = "sip-out-p-" + ts;

        try (SipRtpPeer peer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {

            SipParticipantHandle handle = conf.addSipParticipant(
                    SipParticipantSpec.builder()
                            .participantId(sipPid)
                            .remoteIp("127.0.0.1")
                            .remoteRtpPort(peer.localPort())
                            .codec("PCMU")
                            .dtmfPayloadType(101)
                            .build());
            peer.setTarget("127.0.0.1", handle.localRtpPort());

            // Self-route so the SIP participant's tee has a downstream sink and
            // its own inbound audio gets mixed straight back to it.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(sipPid, sipPid)));

            byte[] tone = JSynTestHelpers.generateSinePcm8k(440.0, 2.0);
            int frameBytes = 320;
            long deadline = System.currentTimeMillis() + 2000;
            int offset = 0;
            while (System.currentTimeMillis() < deadline) {
                byte[] frame = new byte[frameBytes];
                for (int i = 0; i < frameBytes; i++) {
                    frame[i] = tone[(offset + i) % tone.length];
                }
                peer.sendAudioFrame(frame);
                offset += frameBytes;
                Thread.sleep(18);
            }

            // Poll for a short grace period after the send loop ends rather than
            // asserting synchronously — pipeline warm-up / first-buffer latency
            // may not complete within the send window alone. Same deadline-poll
            // idiom as SipRtpPeerTest in this package.
            long checkDeadline = System.currentTimeMillis() + 3000;
            boolean foundPcmu = false;
            while (System.currentTimeMillis() < checkDeadline && !foundPcmu) {
                foundPcmu = peer.capturedPackets().stream().anyMatch(p -> p.payloadType == 0);
                if (!foundPcmu) {
                    Thread.sleep(50);
                }
            }
            assertTrue(foundPcmu,
                    "expected at least one captured packet with PCMU payload type 0 within the grace period");

            conf.removeParticipant(sipPid);
        }
    }

    private static byte[] downsampleS16LE16kTo8k(byte[] pcm16k) {
        int frames16k = pcm16k.length / 2;
        int frames8k = frames16k / 2;
        byte[] out = new byte[frames8k * 2];
        for (int i = 0; i < frames8k; i++) {
            out[i * 2] = pcm16k[i * 4];
            out[i * 2 + 1] = pcm16k[i * 4 + 1];
        }
        return out;
    }
}
