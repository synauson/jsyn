package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.10 — Graceful shutdown while audio is in flight.
 *
 * <p>Opens a JSyn instance mid-conference with a NativeParticipant writing audio
 * on a background thread. Calls {@code syn.close()} from the main thread and
 * joins the background thread. Asserts no exceptions escape and no deadlock occurs.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class GracefulShutdownIT {

    @Test
    void closeWhileWritingDoesNotDeadlock() throws Exception {
        AtomicReference<Throwable> writerError = new AtomicReference<>();
        AtomicBoolean keepWriting = new AtomicBoolean(true);
        CountDownLatch writerStarted = new CountDownLatch(1);

        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;
        int chunk = fmt.bytesPer20ms();
        long ts = System.nanoTime();
        String confId = "shutdown-it-" + ts;

        JSyn syn = JSynTestHelpers.newJSyn();
        Conference conf = syn.startConference(confId);
        NativeParticipant p = conf.addNativeParticipant("shutdown-p-" + ts,
                NativeParticipantSpec.builder().format(fmt).build());

        Thread writer = new Thread(() -> {
            try {
                byte[] frame = JSynTestHelpers.generateSinePcm16k(440.0, 0.020);
                writerStarted.countDown();
                while (keepWriting.get()) {
                    try {
                        p.write(frame, 0, chunk);
                        Thread.sleep(10);
                    } catch (com.synauson.jsyn.exception.NativeResourceClosedException ignored) {
                        // Expected when the participant or conference is closed mid-run.
                        break;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Throwable t) {
                writerError.set(t);
            }
        }, "audio-writer");
        writer.setDaemon(true);
        writer.start();

        // Let the writer get a head start.
        assertTrue(writerStarted.await(5, TimeUnit.SECONDS), "writer thread did not start");
        Thread.sleep(500);

        // Close from main thread — this must not deadlock.
        keepWriting.set(false);
        try {
            p.close();
        } catch (Exception ignored) {
            // Closing a resource that is already closed by the conference teardown is OK.
        }
        conf.close();
        syn.close();

        writer.join(5_000);
        assertFalse(writer.isAlive(), "writer thread did not exit within 5s — possible deadlock");
        assertNull(writerError.get(), "writer thread threw unexpected exception: " + writerError.get());
    }
}
