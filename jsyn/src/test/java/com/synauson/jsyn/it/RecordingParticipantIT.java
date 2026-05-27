package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.RecordingParticipantHandle;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.RecordingParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.2 — RecordingParticipant records a WAV file.
 *
 * <p>End-to-end JNI-driven integration test for the recording subsystem.
 * Exercises the deadlock-fix path covered by
 * {@code synauson-core/tests/recording_terminate_smoke.rs}:
 * NativeParticipant pumps silence; RecordingParticipant captures via the
 * router; {@code conf.close()} routes through {@code ConferenceActor::terminate}
 * which drains router connections before {@code RecordingParticipant::close}
 * (using the EOS-probe-wait pattern).
 */
class RecordingParticipantIT {

    @Test
    void recordsWavForBoundedDuration(@TempDir Path tmp) throws Exception {
        long ts = System.nanoTime();
        Path output = tmp.resolve("rec.wav");
        String confId = "rec-it-" + ts;
        String srcPid = "rec-src-" + ts;
        String recPid = "rec-p-" + ts;

        NativeAudioFormat fmt = NativeAudioFormat.PCM_S16LE16K_MONO;

        try (JSyn syn = JSynTestHelpers.newJSyn()) {
            Conference conf = syn.startConference(confId);

            com.synauson.jsyn.participant.NativeParticipant src =
                    conf.addNativeParticipant(srcPid,
                            NativeParticipantSpec.builder().format(fmt).build());

            RecordingParticipantHandle rec = conf.addRecordingParticipant(
                    RecordingParticipantSpec.builder()
                            .id(recPid)
                            .sourceParticipantId(srcPid)
                            .outputPath(output.toString())
                            .build());
            assertNotNull(rec);

            byte[] silence = new byte[fmt.bytesPer20ms()];
            long deadline = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < deadline) {
                src.write(silence, 0, silence.length);
                Thread.sleep(15);
            }

            conf.close();
            Thread.sleep(1000);
        }

        assertTrue(Files.exists(output), "output WAV must exist");
        assertTrue(Files.size(output) > 1024, "output WAV must be >1KB");
        byte[] head = new byte[4];
        try (InputStream in = Files.newInputStream(output)) {
            int read = in.read(head);
            assertEquals(4, read);
        }
        assertArrayEquals(new byte[]{'R', 'I', 'F', 'F'}, head,
                "output must start with RIFF header");
    }
}
