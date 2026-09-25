package com.synauson.jsyn.it.support;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Tone measurement for audio captured off the wire or out of a native
 * participant's egress ring: a Goertzel filter per frequency, evaluated over
 * consecutive fixed-length windows so a tone that only starts part-way
 * through a capture (pipeline warm-up, jitter buffer fill) still registers.
 */
public final class ToneAnalysis {

    private ToneAnalysis() {}

    /**
     * Amplitude of {@code freqHz} in {@code samples[off, off + len)}, in sample
     * units: a full-period sine of peak amplitude A measures close to A.
     */
    public static double amplitude(short[] samples, int off, int len, double rate, double freqHz) {
        double w = 2.0 * Math.PI * freqHz / rate;
        double coeff = 2.0 * Math.cos(w);
        double s1 = 0.0;
        double s2 = 0.0;
        for (int i = off; i < off + len; i++) {
            double s0 = samples[i] + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        double power = s1 * s1 + s2 * s2 - coeff * s1 * s2;
        return 2.0 * Math.sqrt(Math.max(power, 0.0)) / len;
    }

    /**
     * The strongest window in which every one of {@code freqsHz} is present
     * together: for each consecutive {@code window}-sample block, the weakest
     * of the frequencies' amplitudes; the maximum of that over all blocks.
     * Returns 0 when {@code samples} holds less than one window.
     */
    public static double strongestJointAmplitude(short[] samples, double rate, int window,
                                                 double... freqsHz) {
        double best = 0.0;
        for (int off = 0; off + window <= samples.length; off += window) {
            double weakest = Double.MAX_VALUE;
            for (double f : freqsHz) {
                weakest = Math.min(weakest, amplitude(samples, off, window, rate, f));
            }
            best = Math.max(best, weakest);
        }
        return best;
    }

    /**
     * Poll {@code source} until some window holds every one of {@code freqsHz}
     * at or above {@code threshold}, or {@code timeout} passes. Returns the
     * strongest joint amplitude seen, so a failing assertion can report how
     * close it came.
     */
    public static double awaitJointTone(Supplier<short[]> source, double rate, int window,
                                        double threshold, Duration timeout, double... freqsHz)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        double best = 0.0;
        while (true) {
            best = Math.max(best, strongestJointAmplitude(source.get(), rate, window, freqsHz));
            if (best >= threshold || System.nanoTime() >= deadline) {
                return best;
            }
            Thread.sleep(100);
        }
    }

    /** Decode every PCMU (payload type 0) packet's payload, in arrival order. */
    public static short[] pcmuSamples(List<RtpPacket> packets) {
        int total = 0;
        for (RtpPacket p : packets) {
            if (p.payloadType == 0) {
                total += p.payload.length;
            }
        }
        short[] out = new short[total];
        int n = 0;
        for (RtpPacket p : packets) {
            if (p.payloadType == 0) {
                for (byte b : p.payload) {
                    out[n++] = MuLawCodec.decode(b);
                }
            }
        }
        return out;
    }

    /** Interpret {@code len} bytes of {@code pcm} from {@code off} as signed 16-bit little-endian samples. */
    public static short[] s16le(byte[] pcm, int off, int len) {
        short[] out = new short[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) ((pcm[off + i * 2] & 0xFF) | (pcm[off + i * 2 + 1] << 8));
        }
        return out;
    }
}
