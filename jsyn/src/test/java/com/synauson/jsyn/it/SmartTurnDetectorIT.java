package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.SmartTurnEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.SmartTurnConfig;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.7 — SmartTurn detector emits a TurnResult event after enough speech audio.
 *
 * <p>SmartTurn inference is triggered by VAD speech-end events (see
 * {@code synauson-core/src/detectors/smart_turn.rs}). Both VAD and SmartTurn
 * must be configured on the participant, and real speech audio is required to
 * trigger the VAD → SmartTurn chain.
 *
 * <p>Uses the short_speech.wav fixture. Skips if the fixture or model files are absent.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SmartTurnDetectorIT {

    @Test
    void turnResultEventArrivesAfterEnoughAudio() throws Exception {
        // SmartTurn requires real speech (VAD fires first), and both model files.
        java.nio.file.Path speechWav = Paths.get(
                "../../../synauson-server/tests/fixtures/short_speech.wav").toAbsolutePath();
        java.nio.file.Path modelsDir = Paths.get("../../../models").toAbsolutePath();
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav not found — skipping SmartTurn IT");
        Assumptions.assumeTrue(modelsDir.resolve("silero_vad.onnx").toFile().exists(),
                "silero_vad.onnx not found — skipping SmartTurn IT");
        Assumptions.assumeTrue(modelsDir.resolve("smart_turn_v3.onnx").toFile().exists(),
                "smart_turn_v3.onnx not found — skipping SmartTurn IT");

        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;
        int chunk = fmt.bytesPer20ms();
        long ts = System.nanoTime();
        String confId = "smart-turn-it-" + ts;
        String pid = "st-p-" + ts;

        byte[] pcm = JSynTestHelpers.readPcmFromWav(speechWav);

        CountDownLatch gotResult = new CountDownLatch(1);
        AtomicBoolean received = new AtomicBoolean(false);

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId);
             NativeParticipant p = conf.addNativeParticipant(pid,
                     NativeParticipantSpec.builder()
                             .format(fmt)
                             // VAD must be enabled — SmartTurn fires on VAD speech-end.
                             .vad(new VadConfig(0.3f, 100, 100))
                             .smartTurn(new SmartTurnConfig(16_000, 0.5f))
                             .build())) {

            // Self-route so tee has a downstream audiomixer; buffers flow to VAD.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));

            try (Subscription sub = conf.streamSmartTurnEvents(pid, ev -> {
                if (ev instanceof SmartTurnEvent.TurnResult) {
                    received.set(true);
                    gotResult.countDown();
                }
            })) {
                // Push speech audio multiple times to trigger VAD + SmartTurn.
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
                assertTrue(arrived, "SmartTurnEvent.TurnResult did not arrive within time limit");
            }
        }
    }
}
