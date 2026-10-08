package com.synauson.jsyn.it;

import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.event.TranscriptEvent;
import com.synauson.jsyn.exception.NotFoundException;
import com.synauson.jsyn.it.support.WebRtcBrowserPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.WebRtcParticipantHandle;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.SttConfig;
import com.synauson.jsyn.spec.TurnConfigUpdate;
import com.synauson.jsyn.spec.VadConfig;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Streaming STT on a real WebRTC call: a headless Chromium speaks the speech fixture as
 * its fake microphone over real ICE, DTLS-SRTP and Opus to a participant with VAD, turn
 * detection and STT, and the words come back in {@code EndOfTurn.text} on the agent event
 * stream and in {@link TranscriptEvent.Turn}.
 *
 * <p>It checks content, not timing: it waits for the STT pool before adding the
 * participant, gives the words a generous deadline, and checks no latency and no drain
 * completeness, so it passes on a loaded machine too.
 */
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class WebRtcSttE2eIT {

    /** Synauson's own webrtcbin STUN URI, as in {@link WebRtcMediaE2eIT}. */
    private static final String STUN = "stun://stun.l.google.com:19302";

    /**
     * Words the fixture says ("... a synauson test fixture for voice activity
     * detection. ..."). The model's other words vary with the CPU; these don't.
     */
    private static final String KEY_WORDS = "voice activity detection";

    /** Everything an observer received, in order. */
    private static final class Recorder<T> implements EventStreamObserver<T> {
        final BlockingQueue<Object> received = new LinkedBlockingQueue<>();

        @Override public void onNext(T event) { received.add(event); }
        @Override public void onError(Throwable t) { received.add(t); }
    }

    /**
     * The letters and digits of {@code text}, lowercase. A turn that ran out of drain
     * time can end inside a word (its last pieces open the next turn), so turns are
     * compared joined, without spaces or punctuation.
     */
    private static String letters(String text) {
        StringBuilder b = new StringBuilder();
        text.codePoints().filter(Character::isLetterOrDigit)
            .forEach(c -> b.appendCodePoint(Character.toLowerCase(c)));
        return b.toString();
    }

    private static boolean saysTheKeyWords(List<AgentEvent.EndOfTurn> ends) {
        StringBuilder said = new StringBuilder();
        for (AgentEvent.EndOfTurn e : ends) said.append(letters(e.text));
        return said.toString().contains(letters(KEY_WORDS));
    }

    /**
     * One second of silence, the fixture's speech, then two seconds of silence, as 16 kHz
     * mono PCM WAV. Chromium loops its fake microphone's file, so the pauses end turns.
     */
    private static Path speechWithPauses(Path fixture) throws Exception {
        ByteBuffer wav = ByteBuffer.wrap(Files.readAllBytes(fixture)).order(ByteOrder.LITTLE_ENDIAN);
        // Walk the RIFF chunks to the samples: the fixture has a LIST chunk.
        int at = 12;
        byte[] speech = null;
        while (at + 8 <= wav.limit()) {
            String id = new String(wav.array(), at, 4, StandardCharsets.US_ASCII);
            int size = wav.getInt(at + 4);
            if (id.equals("data")) {
                speech = new byte[size];
                System.arraycopy(wav.array(), at + 8, speech, 0, size);
                break;
            }
            at += 8 + size + (size % 2);
        }
        assertNotNull(speech, "no data chunk in " + fixture);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        pcm.write(new byte[32_000]);
        pcm.write(speech);
        pcm.write(new byte[64_000]);
        byte[] data = pcm.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(44 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + data.length)
            .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
            .putShort((short) 1).putShort((short) 1)      // PCM, mono
            .putInt(16_000).putInt(32_000)                // sample and byte rate
            .putShort((short) 2).putShort((short) 16)     // block align, bits
            .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
        Path file = Files.createTempFile("jsyn-stt-speech-", ".wav");
        file.toFile().deleteOnExit();
        Files.write(file, out.array());
        return file;
    }

    /**
     * Natives older than STT report no STT, and natives older than the agent stream have
     * no subscribeAgentEvents export: skip on either. Waits for the STT pool first, since
     * closing a runtime while it loads can crash ONNX Runtime.
     */
    private static void assumeSttAndAgentStream(JSyn syn, Conference conf)
            throws InterruptedException {
        assumeTrue(syn.capabilities().stt != null, "native runtime predates STT");
        // Adding an STT participant before the pool is ready throws.
        JSynTestHelpers.awaitSttReady(syn, Duration.ofSeconds(120));
        try {
            conf.streamAgentEvents("no-such-participant", new Recorder<>()).close();
        } catch (UnsatisfiedLinkError e) {
            assumeTrue(false, "native runtime predates the agent event stream");
        } catch (NotFoundException expected) {
            // The export exists.
        }
    }

    @Test
    void speechOverWebRtcComesBackAsTurnText() throws Exception {
        Path fixture = JSynTestHelpers.resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures/short_speech.wav");
        assumeTrue(fixture.toFile().exists(), "short_speech.wav not found");
        Path speech = speechWithPauses(fixture);

        long ts = System.nanoTime();
        String pid = "webrtc-stt-p-" + ts;

        try (JSyn syn = JSynTestHelpers.newJSynWithStt();
             Conference conf = syn.startConference("webrtc-stt-it-" + ts)) {
            assumeSttAndAgentStream(syn, conf);

            try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer(speech.toString())) {
                WebRtcParticipantHandle handle = conf.addWebRtcParticipant(
                        WebRtcParticipantSpec.builder()
                                .participantId(pid)
                                .sdpOffer(browser.createOffer())
                                .stunServer(STUN)
                                .jitterBufferMs(200)
                                .vad(new VadConfig(0.3f, 100, 100))
                                // A pause ends the turn even when turn detection calls it
                                // incomplete.
                                .turnDetection(new TurnDetectionConfig(16_000, 0.5f).withTurns(
                                        TurnConfigUpdate.none().withEndOfTurnTimeoutMs(1_000)))
                                .stt(SttConfig.defaults())
                                .build());

                // Subscribe before the browser has the answer, so no media flows yet.
                Recorder<TranscriptEvent> transcripts = new Recorder<>();
                Recorder<AgentEvent> events = new Recorder<>();
                try (Subscription transcriptSub = conf.streamTranscriptEvents(pid, transcripts);
                     Subscription agentSub = conf.streamAgentEvents(pid, events);
                     Subscription iceSub = conf.streamWebRtcIceCandidates(pid, ev -> {
                         if (!ev.endOfCandidates) {
                             browser.addRemoteIceCandidate(ev.candidate, ev.sdpMLineIndex);
                         }
                     })) {
                    AgentEvent.Subscribed subscribed = assertInstanceOf(AgentEvent.Subscribed.class,
                            events.received.poll(10, TimeUnit.SECONDS), "Subscribed comes first");
                    assertTrue(subscribed.stt, "STT runs on the participant");
                    browser.applyAnswer(handle.sdpAnswer());

                    List<AgentEvent.EndOfTurn> ends = readTurnsUntilTheKeyWords(
                            browser, handle, events, subscribed.streamId);

                    // Each EndOfTurn's text is its turn transcript's, without the
                    // leading space. The transcript stream was open before any audio,
                    // so it has every turn, in the same order. Turns without words are
                    // left out on both streams: newer natives never send one on the
                    // agent stream, but 1.6.0 sends its EndOfTurn with empty text.
                    List<AgentEvent.EndOfTurn> worded = new ArrayList<>();
                    for (AgentEvent.EndOfTurn end : ends) {
                        if (!end.text.isEmpty()) worded.add(end);
                    }
                    ends = worded;
                    List<TranscriptEvent.Turn> turns = new ArrayList<>();
                    while (turns.size() < ends.size()) {
                        Object o = transcripts.received.poll(30, TimeUnit.SECONDS);
                        assertNotNull(o, "a turn transcript per end of turn within 30 s");
                        if (o instanceof Throwable) fail("transcript stream failed", (Throwable) o);
                        if (o instanceof TranscriptEvent.Turn
                                && !((TranscriptEvent.Turn) o).text.trim().isEmpty()) {
                            turns.add((TranscriptEvent.Turn) o);
                        }
                    }
                    for (int i = 0; i < ends.size(); i++) {
                        assertEquals(turns.get(i).text.trim(), ends.get(i).text,
                                "EndOfTurn " + ends.get(i).turnId + " has its turn transcript's text");
                        assertEquals(turns.get(i).complete, ends.get(i).complete);
                    }
                }
                conf.removeParticipant(pid);
            }
        }
    }

    /**
     * Relay the browser's ICE candidates and read the agent stream until the turns' text
     * says the key words, checking its structure on the way: seqs rise, turn ids rise by
     * one from 1, and each EndOfTurn follows its TurnStarted. Words a turn missed (STT
     * out of drain time) open the next one, so the turns are read joined.
     */
    private static List<AgentEvent.EndOfTurn> readTurnsUntilTheKeyWords(
            WebRtcBrowserPeer browser, WebRtcParticipantHandle handle,
            Recorder<AgentEvent> events, long streamId) throws InterruptedException {
        List<AgentEvent.EndOfTurn> ends = new ArrayList<>();
        long lastSeq = 0;
        Long open = null;
        long deadline = System.currentTimeMillis() + 90_000;
        while (!saysTheKeyWords(ends)) {
            assertTrue(System.currentTimeMillis() < deadline,
                    "the key words within 90 s; turns so far: " + texts(ends));
            for (Map<String, Object> candidate : browser.drainLocalIceCandidates()) {
                handle.addIceCandidate((String) candidate.get("candidate"),
                        ((Number) candidate.get("sdpMLineIndex")).intValue());
            }
            Object o = events.received.poll(500, TimeUnit.MILLISECONDS);
            if (o == null) continue;
            if (o instanceof Throwable) fail("agent stream failed", (Throwable) o);
            AgentEvent e = (AgentEvent) o;
            if (!e.isStored()) continue;
            assertEquals(streamId, e.streamId);
            assertTrue(e.seq > lastSeq, "seq rises: " + e.seq + " after " + lastSeq);
            lastSeq = e.seq;
            if (e instanceof AgentEvent.TurnStarted) {
                AgentEvent.TurnStarted t = (AgentEvent.TurnStarted) e;
                assertNull(open, "turn " + t.turnId + " inside turn " + open);
                long previous = ends.isEmpty() ? 0 : ends.get(ends.size() - 1).turnId;
                assertEquals(previous + 1, t.turnId, "turn ids rise by one from 1");
                open = t.turnId;
            } else if (e instanceof AgentEvent.EndOfTurn) {
                AgentEvent.EndOfTurn end = (AgentEvent.EndOfTurn) e;
                System.out.println("end of turn " + end.turnId + " " + end.reason
                        + " complete=" + end.complete + ": " + end.text);
                assertEquals(open, Long.valueOf(end.turnId), "an end follows its start");
                assertNotEquals(AgentEvent.EndOfTurn.STREAM_ENDED, end.reason);
                open = null;
                ends.add(end);
            } else if (e instanceof AgentEvent.Error) {
                AgentEvent.Error err = (AgentEvent.Error) e;
                // A late turn detection decision or STT falling behind on a loaded
                // machine is recoverable; STT or turn detection stopping is not.
                assertTrue(AgentEvent.Error.TURN_DECISION_MISSING.equals(err.reason)
                        || AgentEvent.Error.STT_LAGGING.equals(err.reason),
                        err.reason + ": " + err.message);
            } else if (e instanceof AgentEvent.StreamEnded) {
                fail("the stream ended early");
            }
        }
        return ends;
    }

    private static List<String> texts(List<AgentEvent.EndOfTurn> ends) {
        List<String> texts = new ArrayList<>();
        for (AgentEvent.EndOfTurn e : ends) texts.add(e.text);
        return texts;
    }
}
