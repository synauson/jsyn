package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Shared test helpers for the jsyn integration test suite.
 *
 * <p>Port ranges are allocated atomically to avoid collisions between test
 * classes when tests from different classes run in the same JVM process.
 */
final class JSynTestHelpers {

    /** Each call advances the base by 200 to give each JSyn instance headroom. */
    private static final AtomicInteger PORT_BASE = new AtomicInteger(42000);

    private JSynTestHelpers() {}

    /**
     * Resolve the sibling {@code synauson} repo checkout used for model and
     * fixture files ({@code models/*.onnx},
     * {@code synauson-server/tests/fixtures/short_speech.wav}).
     *
     * <p>CI passes {@code -DsynausonRepoDir=<checkout path>} explicitly,
     * since the two repos are checked out independently there. Local runs
     * fall back to a synauson checkout next to this one (both
     * checkouts in the same parent directory). Two levels up
     * from this Gradle project directory
     * ({@code jsyn/jsyn/}) reaches the shared parent, then back down into
     * the sibling checkout.
     */
    static Path resolveSynausonRepo() {
        String override = System.getProperty("synausonRepoDir");
        if (override != null) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of("../../synauson").toAbsolutePath().normalize();
    }

    private static @Nullable Path modelStore;

    /**
     * A model store holding every model this jsyn release pins, imported once per JVM
     * from the synauson checkout's {@code models/} folder into {@code build/model-store}.
     * Fails (rather than skipping) if the import fails, so a detector test can never
     * pass without its model.
     */
    static synchronized Path modelStore() {
        if (modelStore == null) {
            Path store = Path.of("build", "model-store").toAbsolutePath();
            List<String> imported =
                JSyn.importModels(resolveSynausonRepo().resolve("models"), store);
            if (!imported.containsAll(List.of("sentito-1", "fermata-1"))) {
                throw new IllegalStateException("test model store is missing models: " + imported);
            }
            modelStore = store;
        }
        return modelStore;
    }

    /** Create a new JSyn instance with a unique RTP port range. */
    static JSyn newJSyn() {
        return newJSyn(nextRtpPortMin());
    }

    /**
     * Reserve a unique 200-port RTP range for one JSyn instance and return
     * its (even) lower bound; pass it to {@link #newJSyn(int)}. Tests that
     * need to know which ports synauson will allocate from use this pair.
     */
    static int nextRtpPortMin() {
        return PORT_BASE.getAndAdd(200);
    }

    /**
     * Create a new JSyn instance allocating RTP ports from {@code [rtpMin, rtpMin + 199]}.
     * STT and TTS are off: a runtime licensed for them otherwise loads their models in
     * the background. Tests that decode speech use {@link #newJSynWithStt()}, tests that
     * speak {@link #newJSynWithTts()}.
     */
    static JSyn newJSyn(int rtpMin) {
        return new JSyn(JSynConfig.builder()
                .modelStore(modelStore().toString())
                .rtpPortMin(rtpMin)
                .rtpPortMax(rtpMin + 199)
                .sttCapacity(null, null, 0)
                .ttsCapacity(null, null, 0)
                .build());
    }

    /**
     * Create a JSyn instance with STT on: one decoding worker of four threads and two
     * streams, which skips calibration (timed decodes at startup). The pool still loads
     * in the background; wait for it with {@link #awaitSttReady}.
     */
    static JSyn newJSynWithStt() {
        int rtpMin = nextRtpPortMin();
        return new JSyn(JSynConfig.builder()
                .modelStore(modelStore().toString())
                .rtpPortMin(rtpMin)
                .rtpPortMax(rtpMin + 199)
                .sttCapacity(1, 4, 2)
                .ttsCapacity(null, null, 0)
                .build());
    }

    /**
     * Create a JSyn instance with TTS on (one synthesis worker of two threads, four
     * utterances at once) and STT off. The engine still loads in the background; wait
     * for it with {@link #awaitTtsReady}.
     */
    static JSyn newJSynWithTts() {
        int rtpMin = nextRtpPortMin();
        return new JSyn(JSynConfig.builder()
                .modelStore(modelStore().toString())
                .rtpPortMin(rtpMin)
                .rtpPortMax(rtpMin + 199)
                .sttCapacity(null, null, 0)
                .ttsCapacity(1, 2, 4)
                .build());
    }

    /**
     * Wait until {@code capabilities().tts.state} is {@code "ready"}: adding a participant
     * with a speaker before then throws, and closing a runtime while the engine loads can
     * crash ONNX Runtime. Fails if it fails, or isn't ready in time ({@code idle} says
     * why: no license yet, or no lettura in the store).
     */
    static void awaitTtsReady(JSyn syn, java.time.Duration within) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        while (true) {
            com.synauson.jsyn.Capabilities.TtsCapacity tts = syn.capabilities().tts;
            if (tts == null) {
                throw new IllegalStateException("the native runtime reports no TTS");
            }
            if ("ready".equals(tts.state)) {
                return;
            }
            if ("failed".equals(tts.state)) {
                throw new IllegalStateException("TTS failed: " + tts.detail);
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("TTS not ready within " + within + ": "
                        + tts.state + " (" + tts.detail + ")");
            }
            Thread.sleep(200);
        }
    }

    /**
     * Wait until {@code capabilities().stt.state} is {@code "ready"}. Adding a participant
     * with STT before then throws, and closing a runtime while the pool loads can crash
     * ONNX Runtime. Fails if the pool fails, or isn't ready in time ({@code idle} says
     * why: no license yet, or no STT model in the store).
     */
    static void awaitSttReady(JSyn syn, java.time.Duration within) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        while (true) {
            com.synauson.jsyn.Capabilities.SttCapacity stt = syn.capabilities().stt;
            if (stt == null) {
                throw new IllegalStateException("the native runtime reports no STT");
            }
            if ("ready".equals(stt.state)) {
                return;
            }
            if ("failed".equals(stt.state)) {
                throw new IllegalStateException("STT failed: " + stt.detail);
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("STT not ready within " + within + ": "
                        + stt.state + " (" + stt.detail + ")");
            }
            Thread.sleep(200);
        }
    }

    /**
     * Return a path to a short WAV file suitable for VAD/TurnDetection tests.
     *
     * <p>Preference order:
     * <ol>
     *   <li>The {@code short_speech.wav} fixture shipped with the server test suite.</li>
     *   <li>Any other {@code .wav} in the same fixtures directory.</li>
     *   <li>A synthetic 2-second 440 Hz sine wave generated in a temp directory.</li>
     * </ol>
     */
    static Path sineWav16k() throws Exception {
        Path speech = resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures/short_speech.wav");
        if (speech.toFile().exists()) {
            return speech;
        }
        Path fixturesDir = resolveSynausonRepo()
                .resolve("synauson-server/tests/fixtures");
        if (fixturesDir.toFile().exists()) {
            java.io.File[] wavs = fixturesDir.toFile().listFiles(f -> f.getName().endsWith(".wav"));
            if (wavs != null && wavs.length > 0) {
                return wavs[0].toPath();
            }
        }
        return generateSineWav(Files.createTempDirectory("jsyn-test-"), "sine-2s-16k.wav");
    }

    /**
     * Generate a minimal RIFF WAV file with a 2-second 440 Hz sine wave
     * at 16 kHz, mono, 16-bit signed little-endian PCM.
     */
    static Path generateSineWav(Path dir, String name) throws Exception {
        Path out = dir.resolve(name);
        int sampleRate = 16_000;
        int seconds = 2;
        int totalSamples = sampleRate * seconds;
        ByteArrayOutputStream pcm = new ByteArrayOutputStream(totalSamples * 2);
        for (int n = 0; n < totalSamples; n++) {
            double t = n / (double) sampleRate;
            short s = (short) (32_000.0 * Math.sin(2.0 * Math.PI * 440.0 * t));
            pcm.write(s & 0xff);
            pcm.write((s >> 8) & 0xff);
        }
        byte[] rawPcm = pcm.toByteArray();
        try (OutputStream os = Files.newOutputStream(out)) {
            writeInt32LE(os, 0x46464952); // "RIFF"
            writeInt32LE(os, rawPcm.length + 36);
            writeInt32LE(os, 0x45564157); // "WAVE"
            writeInt32LE(os, 0x20746d66); // "fmt "
            writeInt32LE(os, 16);         // chunk size
            writeInt16LE(os, 1);          // PCM
            writeInt16LE(os, 1);          // mono
            writeInt32LE(os, sampleRate);
            writeInt32LE(os, sampleRate * 2); // byte rate
            writeInt16LE(os, 2);          // block align
            writeInt16LE(os, 16);         // bits per sample
            writeInt32LE(os, 0x61746164); // "data"
            writeInt32LE(os, rawPcm.length);
            os.write(rawPcm);
        }
        return out;
    }

    /** Read raw PCM bytes from a WAV file, skipping the 44-byte RIFF header. */
    static byte[] readPcmFromWav(Path wav) throws Exception {
        byte[] all = Files.readAllBytes(wav);
        int pcmLen = all.length - 44;
        if (pcmLen <= 0) throw new IllegalArgumentException("WAV too short: " + wav);
        byte[] pcm = new byte[pcmLen];
        System.arraycopy(all, 44, pcm, 0, pcmLen);
        return pcm;
    }

    /**
     * Generate a synthetic PCM buffer of a sine wave at the given frequency
     * for the specified duration in seconds, in 16-bit signed little-endian
     * format, at 16 kHz mono.
     */
    static byte[] generateSinePcm16k(double freqHz, double durationSeconds) {
        int sampleRate = 16_000;
        int totalSamples = (int) (sampleRate * durationSeconds);
        byte[] buf = new byte[totalSamples * 2];
        for (int n = 0; n < totalSamples; n++) {
            double t = n / (double) sampleRate;
            short s = (short) (32_000.0 * Math.sin(2.0 * Math.PI * freqHz * t));
            buf[n * 2]     = (byte) (s & 0xff);
            buf[n * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return buf;
    }

    /**
     * Generate a synthetic PCM buffer of a sine wave at the given frequency
     * for the specified duration in seconds, in 16-bit signed little-endian
     * format, at 8 kHz mono — the SIP/PCMU clock rate.
     *
     * <p>Amplitude is halved relative to {@link #generateSinePcm16k} (16000
     * vs 32000) as headroom before mu-law encoding, which compands more
     * aggressively near full scale.
     */
    static byte[] generateSinePcm8k(double freqHz, double durationSeconds) {
        int sampleRate = 8_000;
        int totalSamples = (int) (sampleRate * durationSeconds);
        byte[] buf = new byte[totalSamples * 2];
        for (int n = 0; n < totalSamples; n++) {
            double t = n / (double) sampleRate;
            short s = (short) (16_000.0 * Math.sin(2.0 * Math.PI * freqHz * t));
            buf[n * 2]     = (byte) (s & 0xff);
            buf[n * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return buf;
    }

    /** Compute RMS energy of a signed-16-bit little-endian PCM buffer. */
    static double computeRmsS16LE(byte[] buf, int len) {
        long sum = 0;
        int frames = len / 2;
        for (int i = 0; i < frames; i++) {
            int lo = buf[i * 2] & 0xff;
            int hi = buf[i * 2 + 1];
            short s = (short) ((hi << 8) | lo);
            sum += (long) s * s;
        }
        if (frames == 0) return 0.0;
        return Math.sqrt((double) sum / frames);
    }

    // -------------------------------------------------------------------------
    // WAV write helpers
    // -------------------------------------------------------------------------

    private static void writeInt32LE(OutputStream os, int v) throws Exception {
        os.write(v & 0xff);
        os.write((v >> 8) & 0xff);
        os.write((v >> 16) & 0xff);
        os.write((v >> 24) & 0xff);
    }

    private static void writeInt16LE(OutputStream os, int v) throws Exception {
        os.write(v & 0xff);
        os.write((v >> 8) & 0xff);
    }
}
