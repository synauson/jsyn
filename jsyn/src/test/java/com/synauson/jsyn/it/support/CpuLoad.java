package com.synauson.jsyn.it.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Busy-loop threads that stand in for a loaded host, one per available
 * processor by default. Participant start-up races in synauson (a source
 * pushing into an element that has not reached PLAYING yet) only show up when
 * the streaming threads are starved, which a quiet developer machine rarely
 * does on its own; synauson's own start-order stress tests use the same
 * technique. Stops on {@link #close()}.
 */
public final class CpuLoad implements AutoCloseable {
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running = true;
    // Written by every hog so the JIT cannot discard the loop as dead code.
    @SuppressWarnings("unused")
    private volatile long sink;

    private CpuLoad(int count) {
        for (int i = 0; i < count; i++) {
            Thread t = new Thread(this::spin, "cpu-load-" + i);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
    }

    /** One hog per available processor. */
    public static CpuLoad perProcessor() {
        return new CpuLoad(Runtime.getRuntime().availableProcessors());
    }

    private void spin() {
        long x = 1;
        while (running) {
            for (int i = 0; i < 100_000; i++) {
                x = x * 6364136223846793005L + 1;
            }
            sink = x;
        }
    }

    /** Number of hog threads running. */
    public int count() {
        return threads.size();
    }

    @Override
    public void close() {
        running = false;
        for (Thread t : threads) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
