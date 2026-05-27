package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Create a new JSyn instance with a unique RTP port range. */
    static JSyn newJSyn() {
        int rtpMin = PORT_BASE.getAndAdd(200);
        return new JSyn(JSynConfig.builder()
                .modelsDir(Path.of("../../../models").toAbsolutePath().toString())
                .rtpPortMin(rtpMin)
                .rtpPortMax(rtpMin + 199)
                .build());
    }

    /**
     * Return a path to a short WAV file suitable for VAD/SmartTurn tests.
     *
     * <p>Preference order:
     * <ol>
     *   <li>The {@code short_speech.wav} fixture shipped with the server test suite.</li>
     *   <li>Any other {@code .wav} in the same fixtures directory.</li>
     *   <li>A synthetic 2-second 440 Hz sine wave generated in a temp directory.</li>
     * </ol>
     */
    static Path sineWav16k() throws Exception {
        Path speech = Path.of("../../../synauson-server/tests/fixtures/short_speech.wav")
                .toAbsolutePath();
        if (speech.toFile().exists()) {
            return speech;
        }
        Path fixturesDir = Path.of("../../../synauson-server/tests/fixtures").toAbsolutePath();
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
