package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.exception.FailedPreconditionException;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** {@link JSyn#importModels} and how detectors behave when the store lacks a model. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ModelStoreIT {

    private static Path workspaceModels() {
        return JSynTestHelpers.resolveSynausonRepo().resolve("models");
    }

    @Test
    void importInstallsModelsInTheStoreLayoutAndIsIdempotent(@TempDir Path tmp)
            throws Exception {
        // The two detector models only: the STT model is 650 MB.
        Path src = Files.createDirectory(tmp.resolve("src"));
        for (String f : List.of("sentito-1.onnx", "fermata-1.onnx")) {
            Files.copy(workspaceModels().resolve(f), src.resolve(f));
        }
        Path store = tmp.resolve("store");
        assertEquals(List.of("sentito-1", "fermata-1"), JSyn.importModels(src, store));
        assertTrue(Files.isRegularFile(store.resolve("sentito-1/5/sentito-1.onnx")));
        assertTrue(Files.isRegularFile(store.resolve("fermata-1/1.0.0-cpu/fermata-1.onnx")));
        assertEquals(List.of("sentito-1", "fermata-1"), JSyn.importModels(src, store));
    }

    @Test
    void importRejectsACorruptModelAndLeavesTheStoreWithoutIt(@TempDir Path tmp)
            throws Exception {
        Path src = Files.createDirectory(tmp.resolve("src"));
        byte[] bytes = Files.readAllBytes(workspaceModels().resolve("sentito-1.onnx"));
        bytes[4096] ^= 0x5a;
        Files.write(src.resolve("sentito-1.onnx"), bytes);
        Path store = tmp.resolve("store");

        FailedPreconditionException e = assertThrows(FailedPreconditionException.class,
                () -> JSyn.importModels(src, store));
        assertTrue(e.getMessage().contains("sha256"), e.getMessage());
        assertFalse(Files.exists(store.resolve("sentito-1/5/sentito-1.onnx")));
    }

    @Test
    void importRejectsAFolderWithNoModels(@TempDir Path tmp) {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class,
                () -> JSyn.importModels(tmp, tmp.resolve("store")));
        assertTrue(e.getMessage().contains("no catalog model files"), e.getMessage());
        assertThrows(InvalidArgumentException.class, () -> JSyn.importModels(null, tmp));
    }

    @Test
    void vadWithAnEmptyStoreFailsBeforeAnythingIsBuilt(@TempDir Path emptyStore) {
        int rtpMin = JSynTestHelpers.nextRtpPortMin();
        JSynConfig cfg = JSynConfig.builder()
                .modelStore(emptyStore.toString())
                .rtpPortMin(rtpMin)
                .rtpPortMax(rtpMin + 199)
                .build();
        long ts = System.nanoTime();
        String confId = "model-store-it-" + ts;
        String pid = "ms-p-" + ts;
        try (JSyn syn = new JSyn(cfg);
             Conference conf = syn.startConference(confId)) {
            FailedPreconditionException e = assertThrows(FailedPreconditionException.class,
                    () -> conf.addNativeParticipant(pid, NativeParticipantSpec.builder()
                            .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                            .vad(VadConfig.defaults())
                            .build()));
            assertTrue(e.getMessage().contains("sentito-1"), e.getMessage());
            assertTrue(e.getMessage().contains("not installed"), e.getMessage());

            // Nothing was half built: a leftover participant would make the
            // same id fail as a duplicate, so re-adding it without VAD works.
            try (NativeParticipant p = conf.addNativeParticipant(pid,
                    NativeParticipantSpec.builder()
                            .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                            .build())) {
                assertEquals(pid, p.id());
            }
        }
    }
}
