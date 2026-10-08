package com.synauson.jsyn.it.support;

import com.synauson.jsyn.participant.NativeParticipant;

import java.util.Arrays;

/**
 * Drives a 16-bit PCM {@link NativeParticipant} in real time on one thread:
 * every 20 ms it writes one frame of a sine tone into the ingress ring (or
 * nothing, for a listen-only participant) and drains the egress ring into an
 * in-memory capture, the same shape as an in-process voice agent's audio loop.
 *
 * <p>Each ring is single-producer/single-consumer; this thread is the only
 * ingress writer and the only egress reader while the pump runs.
 */
public final class NativeAudioPump implements AutoCloseable {
    private static final int AMPLITUDE = 8_000;

    private final NativeParticipant participant;
    private final double toneHz;
    private final Thread thread;
    private final Object lock = new Object();
    private short[] captured = new short[16_000 * 4];
    private int capturedLen;
    private volatile boolean running = true;
    private volatile RuntimeException failure;

    private NativeAudioPump(NativeParticipant participant, double toneHz) {
        if (participant.format().bytesPerFrame() != 2) {
            throw new IllegalArgumentException("NativeAudioPump needs a 16-bit PCM format, got "
                    + participant.format());
        }
        this.participant = participant;
        this.toneHz = toneHz;
        this.thread = new Thread(this::run, "native-audio-pump-" + participant.id());
        this.thread.setDaemon(true);
    }

    /** Start writing a {@code toneHz} sine and capturing egress. */
    public static NativeAudioPump tone(NativeParticipant participant, double toneHz) {
        NativeAudioPump pump = new NativeAudioPump(participant, toneHz);
        pump.thread.start();
        return pump;
    }

    /** Start capturing egress without writing anything. */
    public static NativeAudioPump listen(NativeParticipant participant) {
        return tone(participant, 0.0);
    }

    private void run() {
        int rate = participant.format().sampleRate();
        byte[] frame = new byte[participant.format().bytesPer20ms()];
        byte[] drain = new byte[frame.length * 8];
        long sample = 0;
        long start = System.nanoTime();
        try {
            for (long f = 0; running; f++) {
                if (toneHz > 0) {
                    for (int i = 0; i < frame.length / 2; i++, sample++) {
                        short s = (short) (AMPLITUDE * Math.sin(2.0 * Math.PI * toneHz * sample / rate));
                        frame[i * 2] = (byte) (s & 0xFF);
                        frame[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
                    }
                    participant.write(frame, 0, frame.length);
                }
                int n;
                while ((n = participant.read(drain, 0, drain.length)) > 0) {
                    append(ToneAnalysis.s16le(drain, 0, n - n % 2));
                }
                long sleepNanos = start + (f + 1) * 20_000_000L - System.nanoTime();
                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            if (running) {
                failure = e;
            }
        }
    }

    private void append(short[] samples) {
        synchronized (lock) {
            if (capturedLen + samples.length > captured.length) {
                captured = Arrays.copyOf(captured, Math.max(captured.length * 2, capturedLen + samples.length));
            }
            System.arraycopy(samples, 0, captured, capturedLen, samples.length);
            capturedLen += samples.length;
        }
    }

    /** Egress samples captured since start or the last {@link #clearCaptured()}. */
    public short[] capturedSamples() {
        synchronized (lock) {
            return Arrays.copyOf(captured, capturedLen);
        }
    }

    /** Discard everything captured so far. */
    public void clearCaptured() {
        synchronized (lock) {
            capturedLen = 0;
        }
    }

    /** Non-null if a ring read or write threw while the pump was running. */
    public RuntimeException failure() {
        return failure;
    }

    /** Stop the pump thread; the participant itself stays open. */
    @Override
    public void close() {
        running = false;
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
