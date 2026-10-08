package com.synauson.jsyn.it;

import com.synauson.jsyn.AgentStreamOptions;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.exception.AgentStreamException;
import com.synauson.jsyn.exception.NotFoundException;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The voice-agent event stream: Subscribed first, VAD's speech as SpeechStarted and
 * SpeechStopped in conference time, resuming from a cursor, StreamEnded when the
 * participant goes, and the refusal for a participant without turn detection.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class AgentStreamIT {

    /** What an observer received, in order; {@code END} marks onCompleted. */
    private static final class Recorder implements EventStreamObserver<AgentEvent> {
        static final Object END = new Object();
        final BlockingQueue<Object> received = new LinkedBlockingQueue<>();

        @Override public void onNext(AgentEvent event) { received.add(event); }
        @Override public void onError(Throwable t) { received.add(t); }
        @Override public void onCompleted() { received.add(END); }

        Object next() throws InterruptedException {
            Object o = received.poll(20, TimeUnit.SECONDS);
            assertNotNull(o, "nothing within 20 s");
            return o;
        }

        /** The next stored event, skipping heartbeats. */
        AgentEvent nextStored() throws InterruptedException {
            while (true) {
                Object o = next();
                AgentEvent e = assertInstanceOf(AgentEvent.class, o);
                if (e.isStored()) return e;
            }
        }
    }

    /**
     * Natives older than the agent stream have no subscribeAgentEvents export: probe it
     * with a participant that doesn't exist.
     */
    private static void assumeAgentStreamSupported(Conference conf) {
        try {
            conf.streamAgentEvents("no-such-participant", new Recorder()).close();
        } catch (UnsatisfiedLinkError e) {
            assumeTrue(false, "native runtime predates the agent event stream");
        } catch (NotFoundException expected) {
            // The export exists.
        }
    }

    private static NativeParticipantSpec spec(boolean turnDetection) {
        NativeParticipantSpec.Builder b = NativeParticipantSpec.builder()
            .format(NativeAudioFormat.PCM_S16LE16K_MONO)
            .vad(new VadConfig(0.3f, 100, 100));
        if (turnDetection) b.turnDetection(new TurnDetectionConfig(16_000, 0.5f));
        return b.build();
    }

    @Test
    void speechArrivesInOrderAndAResumeContinuesIt() throws Exception {
        Path speechWav = JSynTestHelpers.resolveSynausonRepo()
            .resolve("synauson-server/tests/fixtures/short_speech.wav");
        assumeTrue(speechWav.toFile().exists(), "short_speech.wav not found");
        byte[] pcm = JSynTestHelpers.readPcmFromWav(speechWav);
        int chunk = NativeAudioFormat.PCM_S16LE16K_MONO.bytesPer20ms();
        long ts = System.nanoTime();
        String pid = "agent-p-" + ts;

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("agent-it-" + ts)) {
            assumeAgentStreamSupported(conf);
            NativeParticipant p = conf.addNativeParticipant(pid, spec(true));
            // Self-route so audio flows through the participant's tee to its detectors.
            conf.updatePartyAudioConnections(new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));
            Recorder live = new Recorder();
            Subscription sub = conf.streamAgentEvents(pid,
                AgentStreamOptions.defaults().withHeartbeatMs(200), live);

            AgentEvent.Subscribed subscribed =
                assertInstanceOf(AgentEvent.Subscribed.class, live.next());
            assertFalse(subscribed.stt);

            // Speech, then silence so VAD hears it stop.
            for (int offset = 0; offset + chunk <= pcm.length; offset += chunk) {
                p.write(pcm, offset, chunk);
                Thread.sleep(15);
            }
            byte[] silence = new byte[chunk];
            for (int i = 0; i < 50; i++) {
                p.write(silence, 0, chunk);
                Thread.sleep(15);
            }

            AgentEvent.SpeechStarted start =
                assertInstanceOf(AgentEvent.SpeechStarted.class, live.nextStored());
            assertEquals(1, start.seq);
            AgentEvent stopEvent;
            do {
                stopEvent = live.nextStored();
            } while (!(stopEvent instanceof AgentEvent.SpeechStopped));
            AgentEvent.SpeechStopped stop = (AgentEvent.SpeechStopped) stopEvent;
            assertTrue(stop.atMs > start.atMs, "conference time moves forward");
            assertEquals(start.streamId, stop.streamId);

            // Resuming after the start replays what followed it.
            Recorder resumed = new Recorder();
            try (Subscription again = conf.streamAgentEvents(pid,
                    AgentStreamOptions.resumeAfter(start), resumed)) {
                AgentEvent first = assertInstanceOf(AgentEvent.Subscribed.class, resumed.next());
                assertEquals(start.seq, first.seq, "Subscribed repeats the cursor");
                assertEquals(start.seq + 1, resumed.nextStored().seq);
            }

            // Another stream's cursor is refused with a reason.
            AgentStreamException mismatch = assertThrows(AgentStreamException.class,
                () -> conf.streamAgentEvents(pid,
                    AgentStreamOptions.resumeAfter(start.streamId ^ 1, start.seq), new Recorder()));
            assertEquals(AgentStreamException.AGENT_STREAM_MISMATCH, mismatch.reason());

            // Removing the participant ends the stream.
            conf.removeParticipant(pid);
            p.close();
            AgentEvent last;
            do {
                last = live.nextStored();
            } while (!(last instanceof AgentEvent.StreamEnded));
            assertEquals(AgentEvent.StreamEnded.PARTICIPANT_REMOVED,
                ((AgentEvent.StreamEnded) last).reason);
            assertSame(Recorder.END, live.next(), "onCompleted follows StreamEnded");
            sub.close();
        }
    }

    @Test
    void aParticipantWithoutTurnDetectionHasNoAgentStream() {
        long ts = System.nanoTime();
        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("agent-it-vad-" + ts);
             NativeParticipant p = conf.addNativeParticipant("vad-only", spec(false))) {
            assumeAgentStreamSupported(conf);
            AgentStreamException e = assertThrows(AgentStreamException.class,
                () -> conf.streamAgentEvents("vad-only", new Recorder()));
            assertEquals(AgentStreamException.TURN_DETECTION_REQUIRED, e.reason());
            assertEquals(-1, e.lastSeq());
        }
    }
}
