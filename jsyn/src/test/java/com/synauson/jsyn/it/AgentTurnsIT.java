package com.synauson.jsyn.it;

import com.synauson.jsyn.AppliedTurnConfig;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.exception.FailedPreconditionException;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.exception.NotFoundException;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.TurnConfigUpdate;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Turns on the voice-agent event stream: the turn config from {@link TurnDetectionConfig#withTurns}
 * in Subscribed, {@code forceEndTurn} ending the open turn with reason MANUAL,
 * {@code updateTurnConfig} answered and announced, and the refusals.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class AgentTurnsIT {

    /** Stored agent events, in order; errors and onCompleted are not expected here. */
    private static final class Recorder implements EventStreamObserver<AgentEvent> {
        final BlockingQueue<AgentEvent> received = new LinkedBlockingQueue<>();

        @Override public void onNext(AgentEvent event) { received.add(event); }
        @Override public void onError(Throwable t) { }
        @Override public void onCompleted() { }

        AgentEvent next() throws InterruptedException {
            AgentEvent e = received.poll(20, TimeUnit.SECONDS);
            assertNotNull(e, "nothing within 20 s");
            return e;
        }

        /** The next event of {@code kind}, skipping the rest. */
        <T extends AgentEvent> T nextOf(Class<T> kind) throws InterruptedException {
            while (true) {
                AgentEvent e = next();
                if (kind.isInstance(e)) return kind.cast(e);
            }
        }
    }

    /** Natives older than turn events have no forceEndTurn export. */
    private static void assumeTurnCommandsSupported(Conference conf) {
        try {
            conf.forceEndTurn("no-such-participant");
        } catch (UnsatisfiedLinkError e) {
            assumeTrue(false, "native runtime predates the turn commands");
        } catch (NotFoundException expected) {
            // The export exists.
        }
    }

    private static NativeParticipantSpec spec(TurnDetectionConfig turnDetection) {
        NativeParticipantSpec.Builder b = NativeParticipantSpec.builder()
            .format(NativeAudioFormat.PCM_S16LE16K_MONO)
            .vad(new VadConfig(0.3f, 100, 100));
        if (turnDetection != null) b.turnDetection(turnDetection);
        return b.build();
    }

    @Test
    void theAgentEndsTurnsAndChangesTheConfig() throws Exception {
        Path speechWav = JSynTestHelpers.resolveSynausonRepo()
            .resolve("synauson-server/tests/fixtures/short_speech.wav");
        assumeTrue(speechWav.toFile().exists(), "short_speech.wav not found");
        byte[] pcm = JSynTestHelpers.readPcmFromWav(speechWav);
        int chunk = NativeAudioFormat.PCM_S16LE16K_MONO.bytesPer20ms();
        long ts = System.nanoTime();
        String pid = "turns-p-" + ts;

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("turns-it-" + ts)) {
            assumeTurnCommandsSupported(conf);

            // A turn config out of range refuses the add.
            assertThrows(InvalidArgumentException.class, () -> conf.addNativeParticipant(pid,
                spec(TurnDetectionConfig.defaults().withTurns(
                    TurnConfigUpdate.none().withEndOfTurnTimeoutMs(70_000)))));

            // Only the agent ends turns here: Turn detection never reaches 1.0, no timeout.
            NativeParticipant p = conf.addNativeParticipant(pid, spec(
                new TurnDetectionConfig(16_000, 0.5f).withTurns(TurnConfigUpdate.none()
                    .withEndOfTurnThreshold(1.0f).withEndOfTurnTimeoutMs(0))));
            conf.updatePartyAudioConnections(new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));
            Recorder events = new Recorder();
            try (Subscription sub = conf.streamAgentEvents(pid, events)) {
                AgentEvent.Subscribed subscribed = events.nextOf(AgentEvent.Subscribed.class);
                assertNotNull(subscribed.turnConfig);
                assertEquals(1.0f, subscribed.turnConfig.endOfTurnThreshold, 1e-6);
                assertEquals(0, subscribed.turnConfig.endOfTurnTimeoutMs);
                assertEquals(OptionalLong.empty(), conf.forceEndTurn(pid), "no turn yet");

                for (int offset = 0; offset + chunk <= pcm.length; offset += chunk) {
                    p.write(pcm, offset, chunk);
                    Thread.sleep(15);
                }
                AgentEvent.TurnStarted turn = events.nextOf(AgentEvent.TurnStarted.class);
                assertEquals(1, turn.turnId);
                OptionalLong forced = conf.forceEndTurn(pid);
                assertEquals(OptionalLong.of(turn.turnId), forced);
                AgentEvent.EndOfTurn end = events.nextOf(AgentEvent.EndOfTurn.class);
                assertEquals(turn.turnId, end.turnId);
                assertEquals(AgentEvent.EndOfTurn.MANUAL, end.reason);
                assertEquals("", end.text, "no STT here");

                assertThrows(InvalidArgumentException.class, () -> conf.updateTurnConfig(pid,
                    TurnConfigUpdate.none().withEagerThreshold(1.5f)));
                AppliedTurnConfig applied = conf.updateTurnConfig(pid,
                    TurnConfigUpdate.none().withEndOfTurnTimeoutMs(500));
                assertEquals(500, applied.config.endOfTurnTimeoutMs);
                assertEquals(1.0f, applied.config.endOfTurnThreshold, 1e-6, "unchanged fields stay");
                AgentEvent.TurnConfigUpdated announced =
                    events.nextOf(AgentEvent.TurnConfigUpdated.class);
                assertEquals(applied.seq, announced.seq);
                assertNotNull(announced.config);
                assertEquals(500, announced.config.endOfTurnTimeoutMs);
            }
            conf.removeParticipant(pid);
            p.close();
        }
    }

    @Test
    void aParticipantWithoutTurnDetectionHasNoTurns() {
        long ts = System.nanoTime();
        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("turns-it-vad-" + ts);
             NativeParticipant p = conf.addNativeParticipant("vad-only", spec(null))) {
            assumeTurnCommandsSupported(conf);
            assertThrows(FailedPreconditionException.class, () -> conf.forceEndTurn("vad-only"));
            assertThrows(FailedPreconditionException.class,
                () -> conf.updateTurnConfig("vad-only", TurnConfigUpdate.none()));
        }
    }
}
