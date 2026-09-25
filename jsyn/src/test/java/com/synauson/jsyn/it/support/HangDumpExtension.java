package com.synauson.jsyn.it.support;

import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Dumps every Java thread (full stacks, held locks) if a test runs past
 * {@code jsyn.hangDumpAfterMs}, then native stacks via cdb on Windows when
 * {@code jsyn.cdb} points at cdb.exe. Output goes to stderr and
 * {@code build/hang-dumps/}.
 */
public final class HangDumpExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    private static final long AFTER_MS = Long.getLong("jsyn.hangDumpAfterMs", 22_000);
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hang-dump-watchdog");
        t.setDaemon(true);
        return t;
    });
    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(HangDumpExtension.class);

    @Override
    public void beforeTestExecution(ExtensionContext ctx) {
        String name = ctx.getRequiredTestClass().getSimpleName() + "." + ctx.getRequiredTestMethod().getName();
        long start = System.nanoTime();
        ScheduledFuture<?> first = TIMER.schedule(() -> dump(name, start, "a"), AFTER_MS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> second = TIMER.schedule(() -> dump(name, start, "b"), AFTER_MS + 5_000, TimeUnit.MILLISECONDS);
        ctx.getStore(NS).put("f", new ScheduledFuture<?>[] {first, second});
    }

    @Override
    public void afterTestExecution(ExtensionContext ctx) {
        ScheduledFuture<?>[] fs = ctx.getStore(NS).remove("f", ScheduledFuture[].class);
        if (fs != null) {
            for (ScheduledFuture<?> f : fs) f.cancel(false);
        }
    }

    private static void dump(String name, long start, String tag) {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        StringBuilder sb = new StringBuilder();
        sb.append("=== HANG DUMP ").append(tag).append(' ').append(name)
          .append(" after ").append(elapsed).append(" ms, pid ").append(ProcessHandle.current().pid()).append('\n');
        for (ThreadInfo ti : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            sb.append('"').append(ti.getThreadName()).append("\" id=").append(ti.getThreadId())
              .append(' ').append(ti.getThreadState());
            if (ti.isInNative()) sb.append(" (in native)");
            if (ti.getLockName() != null) sb.append(" on ").append(ti.getLockName());
            if (ti.getLockOwnerName() != null) sb.append(" owned by \"").append(ti.getLockOwnerName()).append('"');
            sb.append('\n');
            StackTraceElement[] st = ti.getStackTrace();
            MonitorInfo[] monitors = ti.getLockedMonitors();
            for (int i = 0; i < st.length; i++) {
                sb.append("\tat ").append(st[i]).append('\n');
                for (MonitorInfo mi : monitors) {
                    if (mi.getLockedStackDepth() == i) sb.append("\t- locked ").append(mi).append('\n');
                }
            }
            for (LockInfo li : ti.getLockedSynchronizers()) sb.append("\t- locked sync ").append(li).append('\n');
            sb.append('\n');
        }
        String text = sb.toString();
        System.err.print(text);
        Path dir = Path.of("build", "hang-dumps");
        String base = name + "-" + ProcessHandle.current().pid() + "-" + tag;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(base + "-java.txt"), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("hang dump write failed: " + e);
        }
        String cdb = System.getProperty("jsyn.cdb");
        if (cdb != null && Files.isRegularFile(Path.of(cdb))) {
            // This JVM plus its children (gst-plugin-scanner runs as a child
            // process while the registry is being built).
            nativeStacks(cdb, ProcessHandle.current().pid(), dir.resolve(base + "-native.txt"));
            ProcessHandle.current().descendants().forEach(ph -> {
                String cmd = ph.info().command().orElse("?");
                System.err.println("child pid " + ph.pid() + " " + cmd);
                if (!cmd.toLowerCase().contains("cdb")) {
                    nativeStacks(cdb, ph.pid(), dir.resolve(base + "-child-" + ph.pid() + "-native.txt"));
                }
            });
        }
    }

    private static void nativeStacks(String cdb, long pid, Path out) {
        // -pv: noninvasive attach (threads are suspended while cdb reads them,
        // then resumed on detach). ~*kn: every thread's native stack.
        ProcessBuilder pb = new ProcessBuilder(cdb, "-pv", "-p", Long.toString(pid),
                "-y", "srv*C:\\symcache*https://msdl.microsoft.com/download/symbols",
                "-c", ".reload; |; ~*kn 80; qd")
                .redirectErrorStream(true)
                .redirectOutput(out.toAbsolutePath().toFile());
        try {
            Process p = pb.start();
            if (!p.waitFor(180, TimeUnit.SECONDS)) p.destroyForcibly();
            System.err.println("native stacks written to " + out.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("cdb failed: " + e);
        }
    }
}
