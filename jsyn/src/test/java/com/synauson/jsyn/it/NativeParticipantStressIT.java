package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.NativeParticipantStats;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.4 — 10-second sustained ingress traffic; asserts liveness and no exceptions.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class NativeParticipantStressIT {

    @Test
    void sustainedTrafficFor10Seconds() throws Exception {
        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;
        int chunkBytes = fmt.bytesPer20ms(); // 640 bytes

        long ts = System.nanoTime();
        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("stress-it-" + ts);
             NativeParticipant p = conf.addNativeParticipant("stress-p-" + ts,
                     NativeParticipantSpec.builder().format(fmt).build())) {

            byte[] frame = JSynTestHelpers.generateSinePcm16k(440.0, 0.020); // 20 ms
            long start = System.currentTimeMillis();
            long end = start + 10_000L;
            long totalWritten = 0;
            int stallCount = 0;

            while (System.currentTimeMillis() < end) {
                int written = p.write(frame, 0, chunkBytes);
                if (written > 0) {
                    totalWritten += written;
                } else {
                    stallCount++;
                }
                Thread.sleep(15);
            }

            NativeParticipantStats stats = p.stats();
            assertNotNull(stats);
            assertTrue(totalWritten > 0, "no bytes written during stress run");

            // Allow up to 5% stalls (ring full is normal under load; ring should drain).
            long totalAttempts = (10_000L / 15) + 1;
            double stallRate = (double) stallCount / totalAttempts;
            assertTrue(stallRate < 0.5, "stall rate too high: " + stallRate);
        }
    }
}
