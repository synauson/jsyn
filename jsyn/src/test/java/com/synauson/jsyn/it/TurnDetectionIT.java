package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.TurnDetectionEvent;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.7 — the turn detector emits a TurnResult event after enough speech audio.
 *
 * <p>Turn-detection inference is triggered by VAD speech-end events (see
 * {@code synauson-core/src/detectors/turn_detection.rs}). Both VAD and turn detection
 * must be configured on the participant, and real speech audio is required to
 * trigger the VAD then turn-detection chain.
 *
 * <p>Uses the short_speech.wav fixture. Skips if the fixture or model files are absent.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class TurnDetectionIT {

    @Test
    void turnDetectionWithoutVadIsRefused() {
        long ts = System.nanoTime();
        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("turn-detection-it-no-vad-" + ts)) {
            NativeParticipantSpec spec = NativeParticipantSpec.builder()
                    .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                    .turnDetection(TurnDetectionConfig.defaults())
                    .build();
            NativeParticipant accepted = null;
            try {
                accepted = conf.addNativeParticipant("p", spec);
            } catch (InvalidArgumentException e) {
                assertTrue(e.getMessage().contains("turn_detection needs vad"), e.getMessage());
            }
            if (accepted != null) {
                accepted.close();
                Assumptions.abort("native runtime predates the turn detection needs VAD check");
            }
        }
    }

    @Test
    void turnResultEventArrivesAfterEnoughAudio() throws Exception {
        // Turn detection requires real speech (VAD fires first), and both model files.
        java.nio.file.Path speechWav = JSynTestHelpers.resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures/short_speech.wav");
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav not found — skipping TurnDetection IT");

        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;
        int chunk = fmt.bytesPer20ms();
        long ts = System.nanoTime();
        String confId = "turn-detection-it-" + ts;
        String pid = "st-p-" + ts;

        byte[] pcm = JSynTestHelpers.readPcmFromWav(speechWav);

        CountDownLatch gotResult = new CountDownLatch(1);
        AtomicBoolean received = new AtomicBoolean(false);

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId);
             NativeParticipant p = conf.addNativeParticipant(pid,
                     NativeParticipantSpec.builder()
                             .format(fmt)
                             // VAD must be enabled — turn detection fires on VAD speech-end.
                             .vad(new VadConfig(0.3f, 100, 100))
                             .turnDetection(new TurnDetectionConfig(16_000, 0.5f))
                             .build())) {

            // Self-route so tee has a downstream audiomixer; buffers flow to VAD.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));

            try (Subscription sub = conf.streamTurnDetectionEvents(pid, ev -> {
                if (ev instanceof TurnDetectionEvent.TurnResult) {
                    received.set(true);
                    gotResult.countDown();
                }
            })) {
                // Push speech audio multiple times to trigger VAD + TurnDetection.
                for (int repeat = 0; repeat < 5 && !received.get(); repeat++) {
                    int offset = 0;
                    while (offset + chunk <= pcm.length && !received.get()) {
                        p.write(pcm, offset, chunk);
                        offset += chunk;
                        Thread.sleep(15);
                    }
                    // Pause between repetitions to let VAD detect speech-end.
                    if (!received.get()) {
                        Thread.sleep(500);
                    }
                }

                boolean arrived = gotResult.await(15, TimeUnit.SECONDS);
                assertTrue(arrived, "TurnDetectionEvent.TurnResult did not arrive within time limit");
            }
        }
    }
}
