package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.it.support.CpuLoad;
import com.synauson.jsyn.it.support.SipRtpPeer;
import com.synauson.jsyn.it.support.ToneAnalysis;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.RecordingParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.synauson.jsyn.it.SipMixedSourcesE2eIT.describe;
import static com.synauson.jsyn.it.SipMixedSourcesE2eIT.sipSpec;
import static com.synauson.jsyn.it.support.ToneAnalysis.awaitJointTone;
import static com.synauson.jsyn.it.support.ToneAnalysis.pcmuSamples;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Participant start-up while media is already flowing, over real UDP in real
 * time and under CPU load.
 *
 * <p>Participants used to start their force-live sources and mixers before the
 * elements downstream of them. When the first buffer hit a pad still in NULL
 * the push returned FLUSHING and the source or aggregator paused its task for
 * good, so under load SIP callers came up deaf or mute, and a new recording
 * could record nothing while also stopping its source for every other
 * listener (the flushing travelled back through the source's tee). Every
 * participant now starts sink-first.
 *
 * <p>Each case repeats, because a start-up race only shows on some runs, and
 * runs with one busy thread per processor, as synauson's own start-order
 * stress tests do, so the streaming threads are starved the way a busy host
 * starves them.
 */
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class SipStartOrderE2eIT {

    private static final double CALLER_TONE_HZ = 440.0;
    private static final double LISTENER_TONE_HZ = 1000.0;
    private static final double PRESENT = 1000.0;
    private static final int WINDOW_8K = 1600;
    private static final Duration HEAR_WITHIN = Duration.ofSeconds(5);
    private static final int EARLY_MEDIA_ITERATIONS = 8;
    private static final int RECORDING_ITERATIONS = 8;
    /** Even ports at the bottom of the test's RTP range that early media is sprayed at. */
    private static final int EARLY_MEDIA_CANDIDATE_PAIRS = 16;

    /**
     * A peer that is already sending RTP when its SIP participant is added (a
     * provider's early media) must be heard, and must hear the conference,
     * every time. The other end is a second SIP participant whose own peer
     * streams a 1 kHz tone, so both directions are measured on the wire.
     *
     * <p>jsyn only reports the participant's local port once
     * {@code addSipParticipant} returns, so the peer sprays its tone at the
     * lowest even ports of this JSyn's private RTP range beforehand, skipping
     * the port the listener already holds. synauson allocates the lowest free
     * pair and no other caller holds one while the next is added, so the new
     * participant's pre-bound socket has the tone queued on it before its
     * pipeline starts.
     */
    @Test
    void sipParticipantWithEarlyMediaStartsHearingAndHeardEveryTime() throws Exception {
        long ts = System.nanoTime();
        String listener = "early-listener-" + ts;
        int rtpMin = JSynTestHelpers.nextRtpPortMin();
        List<String> failures = new ArrayList<>();

        try (SipRtpPeer listenerPeer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn(rtpMin);
             Conference conf = syn.startConference("early-conf-" + ts)) {

            SipParticipantHandle listenerHandle = conf.addSipParticipant(sipSpec(listener, listenerPeer, 101));
            listenerPeer.setTarget("127.0.0.1", listenerHandle.localRtpPort());
            listenerPeer.startTone(LISTENER_TONE_HZ);

            try (CpuLoad load = CpuLoad.perProcessor()) {
                for (int i = 0; i < EARLY_MEDIA_ITERATIONS; i++) {
                    String caller = "early-caller-" + ts + "-" + i;
                    try (SipRtpPeer callerPeer = new SipRtpPeer()) {
                        int[] candidates = IntStream.range(0, EARLY_MEDIA_CANDIDATE_PAIRS)
                                .map(k -> rtpMin + 2 * k)
                                .filter(p -> p != listenerHandle.localRtpPort()
                                        && p != listenerPeer.localPort()
                                        && p != callerPeer.localPort())
                                .toArray();
                        callerPeer.setTargets("127.0.0.1", candidates);
                        callerPeer.startTone(CALLER_TONE_HZ);
                        Thread.sleep(100); // let packets queue before the participant exists

                        SipParticipantHandle handle = conf.addSipParticipant(sipSpec(caller, callerPeer, 101));
                        int port = handle.localRtpPort();
                        assertTrue(IntStream.of(candidates).anyMatch(p -> p == port),
                                "iteration " + i + ": synauson allocated RTP port " + port
                                        + ", outside the early-media candidates starting at " + rtpMin);
                        callerPeer.setTarget("127.0.0.1", port);

                        conf.updatePartyAudioConnections(new ConnectionMatrix(
                                ConnectionEntry.connect(caller, listener),
                                ConnectionEntry.connect(listener, caller)));
                        callerPeer.clearCaptured();
                        listenerPeer.clearCaptured();

                        double heard = awaitJointTone(() -> pcmuSamples(listenerPeer.capturedPackets()),
                                8000, WINDOW_8K, PRESENT, HEAR_WITHIN, CALLER_TONE_HZ);
                        double hears = awaitJointTone(() -> pcmuSamples(callerPeer.capturedPackets()),
                                8000, WINDOW_8K, PRESENT, HEAR_WITHIN, LISTENER_TONE_HZ);
                        if (heard < PRESENT || hears < PRESENT) {
                            failures.add("iteration " + i + ": caller->listener 440 Hz peak " + (int) heard
                                    + ", listener->caller 1 kHz peak " + (int) hears
                                    + ", caller packetsReceived " + handle.stats().packetsReceived
                                    + "; caller peer " + describe(callerPeer.capturedPackets()));
                        }

                        conf.updatePartyAudioConnections(ConnectionMatrix.empty());
                        conf.removeParticipant(caller);
                    }
                }
                assertTrue(failures.isEmpty(), failures.size() + " of " + EARLY_MEDIA_ITERATIONS
                        + " SIP participants with early media came up deaf or mute (" + load.count()
                        + " CPU hogs):\n  " + String.join("\n  ", failures));
            }
        }
    }

    /**
     * Recording a SIP caller that is already streaming must capture the
     * caller, and must not cut the caller off from anyone already hearing it.
     */
    @Test
    void recordingAStreamingSipCallerCapturesItAndKeepsItAudible(@TempDir Path tmp) throws Exception {
        long ts = System.nanoTime();
        String caller = "rec-caller-" + ts;
        String listener = "rec-listener-" + ts;
        List<String> failures = new ArrayList<>();

        try (SipRtpPeer callerPeer = new SipRtpPeer();
             SipRtpPeer listenerPeer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("rec-conf-" + ts)) {

            SipParticipantHandle callerHandle = conf.addSipParticipant(sipSpec(caller, callerPeer, 101));
            callerPeer.setTarget("127.0.0.1", callerHandle.localRtpPort());
            callerPeer.startTone(CALLER_TONE_HZ);
            SipParticipantHandle listenerHandle = conf.addSipParticipant(sipSpec(listener, listenerPeer, 101));
            listenerPeer.setTarget("127.0.0.1", listenerHandle.localRtpPort());
            conf.updatePartyAudioConnections(new ConnectionMatrix(
                    ConnectionEntry.connect(caller, listener)));

            double before = awaitJointTone(() -> pcmuSamples(listenerPeer.capturedPackets()), 8000,
                    WINDOW_8K, PRESENT, Duration.ofSeconds(10), CALLER_TONE_HZ);
            assertTrue(before >= PRESENT, "listener never heard the caller before any recording"
                    + " (peak " + (int) before + "); " + describe(listenerPeer.capturedPackets()));

            // Only the recordings' own start-up runs under load.
            try (CpuLoad load = CpuLoad.perProcessor()) {
                for (int i = 0; i < RECORDING_ITERATIONS; i++) {
                    String rec = "rec-" + ts + "-" + i;
                    Path wav = tmp.resolve(rec + ".wav");
                    conf.addRecordingParticipant(RecordingParticipantSpec.builder()
                            .id(rec)
                            .sourceParticipantId(caller)
                            .outputPath(wav.toString())
                            .build());
                    Thread.sleep(300);
                    listenerPeer.clearCaptured();
                    double after = awaitJointTone(() -> pcmuSamples(listenerPeer.capturedPackets()), 8000,
                            WINDOW_8K, PRESENT, HEAR_WITHIN, CALLER_TONE_HZ);
                    // The old failure could land after the first buffers got through,
                    // so the caller must still be audible a moment later too.
                    listenerPeer.clearCaptured();
                    Thread.sleep(700);
                    double still = ToneAnalysis.strongestJointAmplitude(
                            pcmuSamples(listenerPeer.capturedPackets()), 8000, WINDOW_8K, CALLER_TONE_HZ);
                    conf.removeParticipant(rec);

                    double recorded = recordedToneAmplitude(wav, CALLER_TONE_HZ);
                    if (after < PRESENT || still < PRESENT || recorded < PRESENT) {
                        failures.add("recording " + i + ": listener heard 440 Hz at " + (int) after
                                + " right after the recording was added and " + (int) still
                                + " 0.7 s later, WAV 440 Hz peak " + (int) recorded
                                + " (" + (Files.exists(wav) ? Files.size(wav) + " bytes" : "missing")
                                + "), caller packetsReceived " + callerHandle.stats().packetsReceived);
                    }
                }
                assertTrue(failures.isEmpty(), failures.size() + " of " + RECORDING_ITERATIONS
                        + " recordings of a streaming SIP caller failed (" + load.count()
                        + " CPU hogs):\n  " + String.join("\n  ", failures));
            }
        }
    }

    /**
     * Strongest 200 ms amplitude of {@code freqHz} in a 16-bit PCM WAV, polling
     * briefly for the file to be finalized after the recording is removed.
     * Returns 0 for a missing, headerless or empty file.
     */
    private static double recordedToneAmplitude(Path wav, double freqHz) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        double best = 0.0;
        do {
            if (Files.exists(wav)) {
                Wav w = Wav.parse(Files.readAllBytes(wav));
                if (w != null && w.samples.length > 0) {
                    best = ToneAnalysis.strongestJointAmplitude(w.samples, w.sampleRate,
                            w.sampleRate / 5, freqHz);
                    if (best >= PRESENT) {
                        return best;
                    }
                }
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return best;
    }

    /** Mono 16-bit PCM WAV contents, walking RIFF chunks rather than assuming a 44-byte header. */
    private static final class Wav {
        final int sampleRate;
        final short[] samples;

        private Wav(int sampleRate, short[] samples) {
            this.sampleRate = sampleRate;
            this.samples = samples;
        }

        static Wav parse(byte[] bytes) {
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.length < 12 || b.getInt(0) != 0x46464952 /* RIFF */ || b.getInt(8) != 0x45564157 /* WAVE */) {
                return null;
            }
            int rate = 0;
            int channels = 0;
            int bits = 0;
            int pos = 12;
            while (pos + 8 <= bytes.length) {
                int id = b.getInt(pos);
                long size = b.getInt(pos + 4) & 0xFFFFFFFFL;
                int body = pos + 8;
                if (id == 0x20746d66 /* fmt  */ && body + 16 <= bytes.length) {
                    channels = b.getShort(body + 2);
                    rate = b.getInt(body + 4);
                    bits = b.getShort(body + 14);
                } else if (id == 0x61746164 /* data */) {
                    if (rate == 0 || channels != 1 || bits != 16) {
                        return null;
                    }
                    // A recording still being written has a placeholder size; trust the file.
                    int len = (int) Math.min(size, bytes.length - body);
                    return new Wav(rate, ToneAnalysis.s16le(bytes, body, len - len % 2));
                }
                pos = body + (int) Math.min(size + (size & 1), Integer.MAX_VALUE - body);
            }
            return null;
        }
    }
}
