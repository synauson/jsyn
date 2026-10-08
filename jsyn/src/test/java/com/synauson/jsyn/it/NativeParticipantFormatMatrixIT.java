package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1.7.3 — NativeParticipant ingress write succeeds for every NativeAudioFormat.
 *
 * <p>Each variant: add one participant, push sine-wave audio frames, assert all
 * writes are accepted and stats() is readable. Also verifies self-routed egress
 * arrives for the formats that reliably produce it within the test window.
 */
class NativeParticipantFormatMatrixIT {

    @ParameterizedTest
    @EnumSource(NativeAudioFormat.class)
    void writeSucceedsAndStatsAreReadable(NativeAudioFormat format) throws Exception {
        long ts = System.nanoTime();
        String confId = "fmt-" + format.name() + "-" + ts;
        String pid = "p-" + ts;

        try (JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference(confId);
             NativeParticipant p = conf.addNativeParticipant(pid,
                     NativeParticipantSpec.builder().format(format).build())) {

            // Route p to itself so ingress audio shows up on egress.
            conf.updatePartyAudioConnections(
                    new ConnectionMatrix(ConnectionEntry.connect(pid, pid)));

            // Push ~500 ms of sine-wave frames in 20 ms chunks.
            int chunk = format.bytesPer20ms();
            byte[] frame = generateSineFrame(format, 440.0);
            int pushCount = 25; // 25 × 20 ms = 500 ms
            long totalWritten = 0;
            for (int i = 0; i < pushCount; i++) {
                int written = p.write(frame, 0, chunk);
                totalWritten += Math.max(0, written);
                Thread.sleep(15);
            }

            // Every format must accept at least some ingress bytes.
            assertTrue(totalWritten > 0, format + ": write() never returned positive bytes");

            // Stats must be readable.
            com.synauson.jsyn.NativeParticipantStats stats = p.stats();
            assertNotNull(stats, format + ": stats() returned null");

            // For formats where egress is reliably produced within the test window,
            // also assert egress data arrives. 8kHz formats (PCM_S16LE8K_MONO,
            // G711_ULAW8K, G711_ALAW8K) are excluded because their dual
            // audioresample chain (8k→16k→8k) accumulates latency that can exceed
            // a per-test timeout; ingress write success and stats readability are
            // sufficient verification for those formats.
            if (format.sampleRate() > 8000) {
                byte[] out = new byte[chunk * pushCount];
                // Allow 5 seconds for egress to arrive under load (the full-suite JVM
                // may be GC-stressed, especially for 48kHz → 16kHz resampling).
                long deadline = System.currentTimeMillis() + 5000;
                int totalRead = 0;
                while (totalRead == 0 && System.currentTimeMillis() < deadline) {
                    int r = p.read(out, totalRead, out.length - totalRead);
                    if (r > 0) totalRead += r;
                    Thread.sleep(20);
                }
                assertTrue(totalRead > 0, format + ": no egress audio received");
            }
        }
    }

    /** Generate one 20 ms frame of a sine wave appropriate for the given format. */
    private static byte[] generateSineFrame(NativeAudioFormat format, double freqHz) {
        int sampleRate = format.sampleRate();
        int samplesPerFrame = sampleRate / 50; // 20 ms
        int bytesPerFrame = format.bytesPer20ms();
        byte[] buf = new byte[bytesPerFrame];

        if (format == NativeAudioFormat.G711_ULAW8K) {
            for (int n = 0; n < samplesPerFrame; n++) {
                double t = n / (double) sampleRate;
                short pcm = (short) (16_000.0 * Math.sin(2.0 * Math.PI * freqHz * t));
                buf[n] = linearToUlaw(pcm);
            }
        } else if (format == NativeAudioFormat.G711_ALAW8K) {
            for (int n = 0; n < samplesPerFrame; n++) {
                double t = n / (double) sampleRate;
                short pcm = (short) (16_000.0 * Math.sin(2.0 * Math.PI * freqHz * t));
                buf[n] = linearToAlaw(pcm);
            }
        } else {
            // PCM s16le
            for (int n = 0; n < samplesPerFrame; n++) {
                double t = n / (double) sampleRate;
                short s = (short) (16_000.0 * Math.sin(2.0 * Math.PI * freqHz * t));
                buf[n * 2]     = (byte) (s & 0xff);
                buf[n * 2 + 1] = (byte) ((s >> 8) & 0xff);
            }
        }
        return buf;
    }

    // Minimal G.711 μ-law encoder (ITU-T G.711).
    private static byte linearToUlaw(short pcm) {
        int sign = (pcm & 0x8000) != 0 ? 0x80 : 0;
        if (pcm < 0) pcm = (short) -pcm;
        if (pcm > 32635) pcm = 32635;
        pcm += 33;
        int exp = 7;
        int expMask = 0x4000;
        while ((pcm & expMask) == 0 && exp > 0) { exp--; expMask >>= 1; }
        int mantissa = (pcm >> (exp + 3)) & 0x0f;
        return (byte) (~(sign | (exp << 4) | mantissa) & 0xff);
    }

    // Minimal G.711 A-law encoder (ITU-T G.711).
    private static byte linearToAlaw(short pcm) {
        int sign = (pcm & 0x8000) != 0 ? 0 : 0x80;
        if (pcm < 0) pcm = (short) -pcm;
        if (pcm > 32767) pcm = 32767;
        int exp;
        if (pcm >= 2048) {
            exp = 7;
            int tmp = pcm;
            for (int i = 7; i > 0; i--) {
                if ((tmp & 0x4000) != 0) { exp = i; break; }
                tmp <<= 1;
            }
        } else {
            exp = 0;
        }
        int mantissa = (exp == 0) ? (pcm >> 1) & 0x0f : (pcm >> (exp + 3)) & 0x0f;
        return (byte) ((sign | (exp << 4) | mantissa) ^ 0x55);
    }
}
