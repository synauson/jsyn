package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.5 — 5 native participants with concurrent writes from 5 threads;
 * asserts that every write either succeeds or returns 0 (ring full) — no exceptions.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class NativeParticipantConcurrencyIT {

    private static final int PARTICIPANT_COUNT = 5;
    private static final int FRAMES_PER_WRITER = 200;

    @Test
    void concurrentWritersProduceNoExceptions() throws Exception {
        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;
        int chunkBytes = fmt.bytesPer20ms();

        long ts = System.nanoTime();
        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("concurrency-it-" + ts)) {

            List<NativeParticipant> participants = new ArrayList<>();
            for (int i = 0; i < PARTICIPANT_COUNT; i++) {
                participants.add(conf.addNativeParticipant(
                        "cp-" + ts + "-" + i,
                        NativeParticipantSpec.builder().format(fmt).build()));
            }

            ExecutorService pool = Executors.newFixedThreadPool(PARTICIPANT_COUNT);
            CountDownLatch startGate = new CountDownLatch(1);
            AtomicLong totalWritten = new AtomicLong(0);
            AtomicReference<Throwable> error = new AtomicReference<>();

            List<Future<?>> futures = new ArrayList<>();
            for (NativeParticipant p : participants) {
                futures.add(pool.submit(() -> {
                    try {
                        byte[] frame = JSynTestHelpers.generateSinePcm16k(440.0, 0.020);
                        startGate.await();
                        for (int f = 0; f < FRAMES_PER_WRITER; f++) {
                            int written = p.write(frame, 0, chunkBytes);
                            if (written > 0) totalWritten.addAndGet(written);
                            Thread.sleep(10);
                        }
                    } catch (Exception e) {
                        error.compareAndSet(null, e);
                    }
                }));
            }

            startGate.countDown(); // release all writers simultaneously
            pool.shutdown();
            pool.awaitTermination(20, TimeUnit.SECONDS);

            assertNull(error.get(), "writer thread threw: " + error.get());
            assertTrue(totalWritten.get() > 0, "no bytes written across all writers");

            for (NativeParticipant p : participants) {
                p.close();
            }
        }
    }
}
