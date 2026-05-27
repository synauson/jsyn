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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.9c — End-to-end VAD latency measurement.
 *
 * <p>Records the wall-clock time from the first audio write to the arrival
 * of {@link VadEvent.SpeechStart}. Logs the delta and asserts it is under
 * 5 seconds (loose regression alarm — not a strict spec).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class RealVadE2eLatencyIT {

    @Test
    void speechStartLatencyUnder5Seconds() throws Exception {
        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;
        int chunk = fmt.bytesPer20ms();
        long ts = System.nanoTime();
        String confId = "latency-it-" + ts;
        String pid = "lat-p-" + ts;

        // VAD requires actual speech — a pure sine will not trigger Silero VAD.
        Path speechWav = Paths.get("../../../synauson-server/tests/fixtures/short_speech.wav")
                .toAbsolutePath();
        Assumptions.assumeTrue(speechWav.toFile().exists(),
                "short_speech.wav not found — skipping latency IT");
        Path modelsDir = Paths.get("../../../models").toAbsolutePath();
        Assumptions.assumeTrue(modelsDir.resolve("silero_vad.onnx").toFile().exists(),
                "silero_vad.onnx not found — skipping latency IT");

        Path wav = speechWav;
        byte[] pcm = JSynTestHelpers.readPcmFromWav(wav);

        CountDownLatch speechStart = new CountDownLatch(1);
        AtomicLong eventNanos = new AtomicLong(-1);

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId);
             NativeParticipant p = conf.addNativeParticipant(pid,
                     NativeParticipantSpec.builder()
                             .format(fmt)
                             .vad(VadConfig.defaults())
                             .build())) {

            // Self-route to ensure the tee has a downstream sink so buffers flow.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));

            try (Subscription sub = conf.streamVadEvents(pid, ev -> {
                if (ev instanceof VadEvent.SpeechStart && eventNanos.get() < 0) {
                    eventNanos.set(System.nanoTime());
                    speechStart.countDown();
                }
            })) {
                long startNanos = System.nanoTime();

                // Push speech audio at real-time pace (up to 3 loops for short fixtures).
                for (int repeat = 0; repeat < 3 && speechStart.getCount() > 0; repeat++) {
                    int offset = 0;
                    while (offset + chunk <= pcm.length && speechStart.getCount() > 0) {
                        p.write(pcm, offset, chunk);
                        offset += chunk;
                        Thread.sleep(18);
                    }
                }

                boolean arrived = speechStart.await(5, TimeUnit.SECONDS);
                assertTrue(arrived, "SpeechStart did not arrive within time limit");

                long latencyMs = TimeUnit.NANOSECONDS.toMillis(eventNanos.get() - startNanos);
                System.out.printf("[RealVadE2eLatencyIT] SpeechStart latency: %d ms%n", latencyMs);

                assertTrue(latencyMs < 5_000,
                        "SpeechStart latency exceeded 5s regression alarm: " + latencyMs + " ms");
            }
        }
    }
}
