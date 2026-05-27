package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.FileEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.spec.FileParticipantSpec;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class FileParticipantIT {
    @Test
    void filePlaysAndEmitsEvents() throws Exception {
        // Prefer the short_speech.wav shipped with the server test suite.
        // Fall back to generating a synthetic WAV so the test always runs.
        Path speechWav = Path.of("../../../synauson-server/tests/fixtures/short_speech.wav")
                .toAbsolutePath();
        Path fixturesDir = Path.of("../../../synauson-server/tests/fixtures").toAbsolutePath();
        File[] wavs = speechWav.toFile().exists()
                ? new File[]{speechWav.toFile()}
                : new File(fixturesDir.toString()).listFiles(f -> f.getName().endsWith(".wav"));
        if (wavs == null || wavs.length == 0) {
            // Generate a synthetic WAV so the test always runs.
            Path syntheticWav = JSynTestHelpers.generateSineWav(
                    Files.createTempDirectory("jsyn-file-it-"), "sine.wav");
            wavs = new File[]{syntheticWav.toFile()};
        }

        long ts = System.nanoTime();
        String confId = "file-it-" + ts;
        String pid = "file-hold-" + ts;

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId)) {
            CountDownLatch eos = new CountDownLatch(1);
            AtomicBoolean started = new AtomicBoolean(false);

            // Path.toUri() produces the canonical three-slash file URI form on
            // both platforms - "file:///home/..." on Linux,
            // "file:///C:/..." on Windows. The naive "file://" +
            // getAbsolutePath() produces "file://C:\..." on Windows (only two
            // slashes + drive letter + backslashes), and File.toURI() produces
            // a one-slash variant that synauson's stricter URI parser rejects
            // as InvalidArgument.
            String uri = wavs[0].toPath().toUri().toString();
            try (Subscription sub = conf.streamFileEvents(pid, ev -> {
                if (ev instanceof FileEvent.PlaybackStarted) started.set(true);
                if (ev instanceof FileEvent.Eos) eos.countDown();
            })) {
                conf.addFileParticipant(FileParticipantSpec.builder()
                        .id(pid)
                        .uri(uri)
                        .loopPlayback(false)
                        .build());
                assertTrue(eos.await(15, TimeUnit.SECONDS), "EOS not received within 15s");
                assertTrue(started.get(), "PlaybackStarted event not received");
            }
        }
    }
}
