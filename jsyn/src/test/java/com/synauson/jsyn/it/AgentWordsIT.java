package com.synauson.jsyn.it;

import com.synauson.jsyn.Capabilities;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.event.Word;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.SttConfig;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Words on the voice-agent event stream: with STT a turn starts with its first word, its
 * {@code TurnWords} add up to its {@code EndOfTurn.words}, and {@code text} is those
 * words joined by spaces.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class AgentWordsIT {

    /** Agent events, in order. */
    private static final class Recorder implements EventStreamObserver<AgentEvent> {
        final BlockingQueue<AgentEvent> received = new LinkedBlockingQueue<>();

        @Override public void onNext(AgentEvent event) { received.add(event); }
        @Override public void onError(Throwable t) { }
        @Override public void onCompleted() { }

        AgentEvent next() throws InterruptedException {
            AgentEvent e = received.poll(30, TimeUnit.SECONDS);
            assertNotNull(e, "nothing within 30 s");
            return e;
        }
    }

    /** A runtime with STT on: one worker, so no calibration run. */
    private static JSyn newJSynWithStt() {
        int rtpMin = JSynTestHelpers.nextRtpPortMin();
        return new JSyn(JSynConfig.builder()
            .modelStore(JSynTestHelpers.modelStore().toString())
            .rtpPortMin(rtpMin)
            .rtpPortMax(rtpMin + 199)
            .sttCapacity(1, 2, 1)
            .build());
    }

    /** Wait for the STT pool to load; skip when this runtime can't run STT. */
    private static void awaitStt(JSyn syn) throws InterruptedException {
        for (int i = 0; i < 600; i++) {
            Capabilities.SttCapacity stt = syn.capabilities().stt;
            assumeTrue(stt != null, "native runtime predates STT");
            if ("ready".equals(stt.state)) return;
            assumeTrue(!"idle".equals(stt.state), "STT not available: " + stt.detail);
            Thread.sleep(100);
        }
        fail("STT did not load within 60 s");
    }

    @Test
    void withSttTurnsCarryTheirWords() throws Exception {
        Path speechWav = JSynTestHelpers.resolveSynausonRepo()
            .resolve("synauson-server/tests/fixtures/short_speech.wav");
        assumeTrue(speechWav.toFile().exists(), "short_speech.wav not found");
        byte[] pcm = JSynTestHelpers.readPcmFromWav(speechWav);
        int chunk = NativeAudioFormat.PCM_S16LE16K_MONO.bytesPer20ms();
        long ts = System.nanoTime();
        String pid = "words-p-" + ts;

        try (JSyn syn = newJSynWithStt();
             Conference conf = syn.startConference("words-it-" + ts)) {
            // Exiting while the STT pool loads can crash the runtime: wait for it.
            awaitStt(syn);
            // Threshold 0: every pause ends a turn.
            NativeParticipant p = conf.addNativeParticipant(pid, NativeParticipantSpec.builder()
                .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                .vad(new VadConfig(0.3f, 100, 100))
                .turnDetection(new TurnDetectionConfig(16_000, 0.0f))
                .stt(new SttConfig(3_000))
                .build());
            conf.updatePartyAudioConnections(new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));
            Recorder events = new Recorder();
            try (Subscription sub = conf.streamAgentEvents(pid, events)) {
                for (int offset = 0; offset + chunk <= pcm.length; offset += chunk) {
                    p.write(pcm, offset, chunk);
                    Thread.sleep(15);
                }
                byte[] silence = new byte[chunk];
                for (int i = 0; i < 150; i++) {
                    p.write(silence, 0, chunk);
                    Thread.sleep(15);
                }

                AgentEvent.TurnStarted turn = null;
                List<Word> sent = new ArrayList<>();
                AgentEvent.EndOfTurn end = null;
                while (end == null) {
                    AgentEvent e = events.next();
                    if (e instanceof AgentEvent.TurnStarted) {
                        turn = (AgentEvent.TurnStarted) e;
                        assumeTrue(turn.wordBacked, "native runtime predates turn words");
                    } else if (e instanceof AgentEvent.TurnWords) {
                        AgentEvent.TurnWords w = (AgentEvent.TurnWords) e;
                        assertNotNull(turn, "words come after their TurnStarted");
                        assertEquals(turn.turnId, w.turnId);
                        assertFalse(w.words.isEmpty());
                        sent.addAll(w.words);
                    } else if (e instanceof AgentEvent.EndOfTurn) {
                        end = (AgentEvent.EndOfTurn) e;
                    }
                }
                assertNotNull(turn);
                assertEquals(turn.turnId, end.turnId);
                assertFalse(end.words.isEmpty(), "with STT a turn has words");
                assertEquals(sent, end.words, "the TurnWords are the turn's words");
                assertEquals(end.words.stream().map(w -> w.text).collect(Collectors.joining(" ")),
                    end.text);
                long previous = 0;
                for (Word w : end.words) {
                    assertFalse(w.text.isEmpty() || w.text.startsWith(" "), w.toString());
                    assertTrue(w.startMs <= w.endMs, w.toString());
                    assertTrue(w.startMs >= previous, "word times rise: " + end.words);
                    assertNotNull(w.confidence, w.toString());
                    assertTrue(w.confidence >= 0f && w.confidence <= 1f, w.toString());
                    previous = w.startMs;
                }
            }
            conf.removeParticipant(pid);
            p.close();
        }
    }
}
