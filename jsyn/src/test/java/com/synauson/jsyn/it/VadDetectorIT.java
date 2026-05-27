package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.VadEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.6 — VAD detector emits SpeechStart when speech audio is pushed.
 *
 * <p>Reads the short_speech.wav fixture (or generates a synthetic sine) and
 * streams its PCM through a VAD-enabled NativeParticipant. Asserts a
 * {@link VadEvent.SpeechStart} event arrives within 10 seconds.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VadDetectorIT {

    @Test
    void speechStartEventArrivesWithinTimeout() throws Exception {
        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;
        int chunk = fmt.bytesPer20ms();
        long ts = System.nanoTime();
        String confId = "vad-it-" + ts;
        String pid = "vad-p-" + ts;

        // VAD requires actual speech audio — a pure sine wave won't trigger Silero VAD.
        // Use the short_speech.wav fixture. Skip if neither fixture nor models are present.
        Path speechWav = Paths.get("../../../synauson-server/tests/fixtures/short_speech.wav")
                .toAbsolutePath();
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav fixture not found at " + speechWav + " — skipping VAD IT");
        Path modelsDir = Paths.get("../../../models").toAbsolutePath();
        Assumptions.assumeTrue(modelsDir.resolve("silero_vad.onnx").toFile().exists(),
                "silero_vad.onnx not found in " + modelsDir + " — skipping VAD IT");

        Path wav = speechWav;
        byte[] pcm = JSynTestHelpers.readPcmFromWav(wav);

        CountDownLatch speechStart = new CountDownLatch(1);
        AtomicBoolean gotStart = new AtomicBoolean(false);

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId);
             NativeParticipant p = conf.addNativeParticipant(pid,
                     NativeParticipantSpec.builder()
                             .format(fmt)
                             .vad(VadConfig.defaults())
                             .build())) {

            // Route participant to itself so the audiomixer has a downstream consumer,
            // ensuring the tee (where VAD attaches) flows buffers through the pipeline.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));

            try (Subscription sub = conf.streamVadEvents(pid, ev -> {
                if (ev instanceof VadEvent.SpeechStart) {
                    gotStart.set(true);
                    speechStart.countDown();
                }
            })) {
                // Push the WAV PCM in 20 ms chunks, paced to real-time.
                // Push in loop (up to 3×) so even very short fixtures trigger VAD.
                for (int repeat = 0; repeat < 3 && !gotStart.get(); repeat++) {
                    int offset = 0;
                    while (offset + chunk <= pcm.length && !gotStart.get()) {
                        p.write(pcm, offset, chunk);
                        offset += chunk;
                        Thread.sleep(18);
                    }
                }

                boolean arrived = speechStart.await(5, TimeUnit.SECONDS);
                assertTrue(arrived, "VadEvent.SpeechStart did not arrive within timeout");
            }
        }
    }
}
