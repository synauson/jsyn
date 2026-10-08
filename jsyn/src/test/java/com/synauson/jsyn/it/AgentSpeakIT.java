package com.synauson.jsyn.it;

import com.synauson.jsyn.CancelledUtterance;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.event.PlayedWord;
import com.synauson.jsyn.exception.FailedPreconditionException;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.exception.NotFoundException;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.Speak;
import com.synauson.jsyn.spec.TtsConfig;
import com.synauson.jsyn.spec.VadConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A participant's speaker: {@code speak} plays text into the call and the agent stream
 * reports what played ({@code UtteranceStarted}, {@code WordsPlayed} covering the text,
 * {@code UtteranceDone}); {@code cancelUtterance} stops it with an
 * {@code UtteranceInterrupted} that says what was heard; and the refusals carry their
 * reasons. Content, not timing: nothing here asserts latency.
 */
@Timeout(value = 240, unit = TimeUnit.SECONDS)
class AgentSpeakIT {

    /** Agent events, in order, without heartbeats. */
    private static final class Recorder implements EventStreamObserver<AgentEvent> {
        final BlockingQueue<AgentEvent> received = new LinkedBlockingQueue<>();

        @Override public void onNext(AgentEvent event) {
            if (!(event instanceof AgentEvent.Heartbeat)) {
                received.add(event);
            }
        }
        @Override public void onError(Throwable t) { }
        @Override public void onCompleted() { }

        /** Events up to and including the first {@code last} matches, within 60 s. */
        List<AgentEvent> until(Predicate<AgentEvent> last) throws InterruptedException {
            List<AgentEvent> seen = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (true) {
                AgentEvent e = received.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                assertNotNull(e, "timed out; events so far: " + seen);
                seen.add(e);
                if (last.test(e)) {
                    return seen;
                }
            }
        }
    }

    /** Reads the participant's egress, as a caller would hear it, and counts loud frames. */
    private static final class Listener implements AutoCloseable {
        final AtomicInteger loudFrames = new AtomicInteger();
        private final AtomicBoolean stop = new AtomicBoolean();
        private final Thread thread;

        Listener(NativeParticipant p) {
            int frame = NativeAudioFormat.PCM_S16LE16K_MONO.bytesPer20ms();
            thread = new Thread(() -> {
                byte[] buf = new byte[frame];
                while (!stop.get()) {
                    int n = p.read(buf, 0, frame);
                    if (n == 0) {
                        try { Thread.sleep(5); } catch (InterruptedException e) { return; }
                        continue;
                    }
                    if (JSynTestHelpers.computeRmsS16LE(buf, n) > 500.0) {
                        loudFrames.incrementAndGet();
                    }
                }
            }, "speak-it-listener");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() throws InterruptedException {
            stop.set(true);
            thread.join(5_000);
        }
    }

    private static NativeParticipantSpec speaker() {
        return NativeParticipantSpec.builder()
            .format(NativeAudioFormat.PCM_S16LE16K_MONO)
            .vad(VadConfig.defaults())
            .turnDetection(TurnDetectionConfig.defaults())
            .tts(TtsConfig.defaults())
            .build();
    }

    /** The playback events of {@code utteranceId}, by kind, in order. */
    private static List<String> playbackKinds(List<AgentEvent> events, String utteranceId) {
        List<String> kinds = new ArrayList<>();
        for (AgentEvent e : events) {
            if (e instanceof AgentEvent.UtteranceStarted
                    && ((AgentEvent.UtteranceStarted) e).utteranceId.equals(utteranceId)) {
                kinds.add("started");
            } else if (e instanceof AgentEvent.WordsPlayed
                    && ((AgentEvent.WordsPlayed) e).utteranceId.equals(utteranceId)) {
                kinds.add("words");
            } else if (e instanceof AgentEvent.UtteranceDone
                    && ((AgentEvent.UtteranceDone) e).utteranceId.equals(utteranceId)) {
                kinds.add("done");
            } else if (e instanceof AgentEvent.UtteranceInterrupted
                    && ((AgentEvent.UtteranceInterrupted) e).utteranceId.equals(utteranceId)) {
                kinds.add("interrupted");
            } else if (e instanceof AgentEvent.UtteranceFailed
                    && ((AgentEvent.UtteranceFailed) e).utteranceId.equals(utteranceId)) {
                kinds.add("failed: " + ((AgentEvent.UtteranceFailed) e).message);
            }
        }
        return kinds;
    }

    private static List<PlayedWord> playedWords(List<AgentEvent> events, String utteranceId) {
        List<PlayedWord> words = new ArrayList<>();
        for (AgentEvent e : events) {
            if (e instanceof AgentEvent.WordsPlayed
                    && ((AgentEvent.WordsPlayed) e).utteranceId.equals(utteranceId)) {
                words.addAll(((AgentEvent.WordsPlayed) e).words);
            }
        }
        return words;
    }

    @Test
    void aSpokenSentenceIsReportedWordByWordThenCancelled() throws Exception {
        long ts = System.nanoTime();
        String pid = "speak-p-" + ts;
        String quiet = "speak-quiet-" + ts;

        try (JSyn syn = JSynTestHelpers.newJSynWithTts();
             Conference conf = syn.startConference("speak-it-" + ts)) {
            assumeTrue(syn.capabilities().tts != null, "native runtime predates the speaker");
            // Exiting while the engine loads can crash the runtime: wait for it.
            JSynTestHelpers.awaitTtsReady(syn, Duration.ofSeconds(120));
            NativeParticipant p = conf.addNativeParticipant(pid, speaker());
            Recorder events = new Recorder();
            try (Listener listener = new Listener(p);
                 Subscription sub = conf.streamAgentEvents(pid, events)) {
                AgentEvent.Subscribed subscribed = assertInstanceOf(AgentEvent.Subscribed.class,
                    events.until(e -> true).get(0));
                assertTrue(subscribed.tts, "the participant has a speaker");
                assertEquals("en-us-f1", subscribed.voice, "the default voice");

                // A whole sentence: started, its words, done.
                String text = "Thank you for calling. Your appointment is confirmed for Tuesday morning.";
                assertTrue(conf.speak(pid, Speak.complete("u1", text)), "u1 is new");
                List<AgentEvent> seen = events.until(e -> e instanceof AgentEvent.UtteranceDone
                    || e instanceof AgentEvent.UtteranceFailed);
                List<String> kinds = playbackKinds(seen, "u1");
                assertEquals("started", kinds.get(0), kinds.toString());
                assertEquals("done", kinds.get(kinds.size() - 1), kinds.toString());
                assertTrue(kinds.subList(1, kinds.size() - 1).stream().allMatch("words"::equals),
                    kinds.toString());
                AgentEvent.UtteranceStarted started = (AgentEvent.UtteranceStarted) seen.stream()
                    .filter(e -> e instanceof AgentEvent.UtteranceStarted).findFirst().get();
                AgentEvent.UtteranceDone done = (AgentEvent.UtteranceDone) seen.get(seen.size() - 1);
                assertEquals("u1", done.utteranceId);
                assertTrue(done.audioMs > 2_000, "a sentence of audio: " + done.audioMs);
                assertTrue(done.atMs >= started.atMs, "done after started");

                List<PlayedWord> words = playedWords(seen, "u1");
                assertEquals(Arrays.asList(text.split(" ")),
                    words.stream().map(w -> w.text).collect(Collectors.toList()),
                    "every word once, in order, as sent");
                long lastEnd = 0;
                for (PlayedWord w : words) {
                    assertEquals(w.text, text.substring(w.charStart(text), w.charEnd(text)),
                        "offsets slice the sent text: " + w);
                    assertTrue(w.startMs <= w.endMs && w.endMs <= done.audioMs, w.toString());
                    assertTrue(w.startMs >= lastEnd, "times don't go back: " + w);
                    lastEnd = w.endMs;
                }
                assertTrue(listener.loudFrames.get() > 25,
                    "the participant heard speech: " + listener.loudFrames.get() + " loud frames");

                // u1 ended: more text for it is refused, and so is a voice after the first Speak.
                FailedPreconditionException ended = assertThrows(FailedPreconditionException.class,
                    () -> conf.speak(pid, Speak.builder().utteranceId("u1").text("more").build()));
                assertTrue(ended.getMessage().startsWith("UTTERANCE_ENDED"), ended.getMessage());

                // A long utterance, cancelled once it plays.
                String longText = "This is a long announcement that keeps going so that there "
                    + "is plenty of time to cancel it while it is still playing into the call, "
                    + "and nobody should ever hear the end of this sentence at all.";
                assertTrue(conf.speak(pid, Speak.builder().utteranceId("u2").text(longText).build()));
                InvalidArgumentException lateVoice = assertThrows(InvalidArgumentException.class,
                    () -> conf.speak(pid, Speak.builder().utteranceId("u2").voice("en-us-m1").build()));
                assertTrue(lateVoice.getMessage().startsWith("INVALID_SPEAK"), lateVoice.getMessage());
                assertFalse(conf.speak(pid, Speak.builder().utteranceId("u2").text("")
                    .release(Speak.Release.END).build()), "the same id adds to u2");
                events.until(e -> e instanceof AgentEvent.UtteranceStarted
                    && ((AgentEvent.UtteranceStarted) e).utteranceId.equals("u2"));

                List<CancelledUtterance> cancelled = conf.cancelUtterance(pid, "u2");
                assertEquals(1, cancelled.size(), cancelled.toString());
                CancelledUtterance c = cancelled.get(0);
                assertEquals("u2", c.utteranceId);
                assertTrue(longText.startsWith(c.heardText), "heard text is a prefix: " + c);
                assertTrue(c.heardText.length() < longText.length(), "not all of it: " + c);
                List<AgentEvent> afterCancel = events.until(e -> e instanceof AgentEvent.UtteranceInterrupted);
                AgentEvent.UtteranceInterrupted interrupted =
                    (AgentEvent.UtteranceInterrupted) afterCancel.get(afterCancel.size() - 1);
                assertEquals("u2", interrupted.utteranceId);
                assertEquals(AgentEvent.UtteranceInterrupted.CANCELLED, interrupted.reason);
                assertEquals(c.seq, interrupted.seq, "the answer names its event");
                assertEquals(c.heardText, interrupted.heardText);
                assertEquals(c.heardMs, interrupted.heardMs);
                assertEquals(interrupted.heardText.codePointCount(0, interrupted.heardText.length()),
                    interrupted.heardTextEnd);

                // Cancelled again: already over, so nothing; never had: UNKNOWN_UTTERANCE.
                assertTrue(conf.cancelUtterance(pid, "u2").isEmpty());
                NotFoundException unknown = assertThrows(NotFoundException.class,
                    () -> conf.cancelUtterance(pid, "never-spoken"));
                assertTrue(unknown.getMessage().startsWith("UNKNOWN_UTTERANCE"), unknown.getMessage());
                assertTrue(conf.cancelUtterances(pid).isEmpty(), "nothing left to cancel");
            }

            // No speaker, no speech.
            NativeParticipant q = conf.addNativeParticipant(quiet, NativeParticipantSpec.builder()
                .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                .vad(VadConfig.defaults())
                .turnDetection(TurnDetectionConfig.defaults())
                .build());
            FailedPreconditionException noSpeaker = assertThrows(FailedPreconditionException.class,
                () -> conf.speak(quiet, Speak.complete("u", "hello")));
            assertTrue(noSpeaker.getMessage().startsWith("TTS_REQUIRED"), noSpeaker.getMessage());

            conf.removeParticipant(quiet);
            q.close();
            conf.removeParticipant(pid);
            p.close();
        }
    }
}
