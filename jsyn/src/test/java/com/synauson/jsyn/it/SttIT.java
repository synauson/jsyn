package com.synauson.jsyn.it;

import com.synauson.jsyn.Capabilities;
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.exception.FailedPreconditionException;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.SttConfig;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Streaming STT: what the engine refuses, and the STT capacity it reports. Transcript
 * content is tested on the engine side.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SttIT {

    /** Natives older than STT ignore the {@code stt} key and have no transcript stream. */
    private static void assumeSttSupported(JSyn syn) {
        assumeTrue(syn.capabilities().stt != null, "native runtime predates STT");
    }

    @Test
    void sttWithoutTurnDetectionIsRefused() {
        long ts = System.nanoTime();
        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("stt-it-refused-" + ts)) {
            assumeSttSupported(syn);
            InvalidArgumentException e = assertThrows(InvalidArgumentException.class,
                () -> conf.addNativeParticipant("p", NativeParticipantSpec.builder()
                    .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                    .vad(VadConfig.defaults())
                    .stt(SttConfig.defaults())
                    .build()));
            assertTrue(e.getMessage().contains("turn detection"), e.getMessage());
        }
    }

    @Test
    void aParticipantWithoutSttHasNoTranscriptStream() {
        long ts = System.nanoTime();
        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("stt-it-none-" + ts);
             NativeParticipant p = conf.addNativeParticipant("p", NativeParticipantSpec.builder()
                 .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                 .build())) {
            assumeSttSupported(syn);
            assertThrows(FailedPreconditionException.class,
                () -> conf.streamTranscriptEvents("p", ev -> { }));
        }
    }

    @Test
    void capabilitiesReportTheSttCapacity() {
        try (JSyn syn = JSynTestHelpers.newJSyn()) {
            Capabilities.SttCapacity stt = syn.capabilities().stt;
            assumeTrue(stt != null, "native runtime predates STT");
            assertNotNull(stt.state);
            assertNotNull(stt.streams);
            assertTrue(stt.streams.inUse >= 0);
        }
    }
}
