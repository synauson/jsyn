package com.synauson.jsyn.internal;

import com.synauson.jsyn.exception.NativeResourceClosedException;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for objects that own a native resource and must release it when closed.
 *
 * <p>Subclasses pass a {@link Runnable} to the constructor that performs the native
 * free operation (e.g. {@code NativeBridge.unsubscribe(id)}). This runnable is
 * registered with a shared {@link Cleaner}: it will run when the object is
 * {@link #close() closed} explicitly, or — as a safety net — when the GC collects
 * an unclosed instance.
 *
 * <p>Explicit {@link #close()} is idempotent: subsequent calls are no-ops. Subclasses
 * should call {@link #requireOpen()} before delegating to native methods to surface
 * use-after-close early.
 *
 * <p>This class implements {@link AutoCloseable} so instances can be used in
 * try-with-resources blocks.
 *
 * <p>This class is part of jsyn's internal plumbing. Application code should hold the
 * concrete subclass (e.g. {@code JSyn}, {@code Conference}, {@code Subscription}) and
 * rely on its public API rather than this base type directly.
 *
 * @since 0.1.0
 */
public abstract class NativeResource implements AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private final Cleaner.Cleanable cleanable;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Construct a new native resource, registering the {@code nativeFree} runnable with
     * a shared {@link Cleaner} so the resource is released even if {@link #close()} is
     * never called.
     *
     * @param nativeFree runnable that frees the underlying native resource; must not
     *                   capture a reference to {@code this} (would prevent GC collection)
     */
    protected NativeResource(Runnable nativeFree) {
        this.cleanable = CLEANER.register(this, nativeFree);
    }

    /**
     * Release the native resource. Idempotent — subsequent calls are no-ops.
     * The {@code Cleaner} safety net is cancelled on the first call.
     */
    @Override
    public final void close() {
        if (closed.compareAndSet(false, true)) {
            cleanable.clean();
        }
    }

    /**
     * Assert that this resource has not been closed.
     *
     * @throws NativeResourceClosedException if {@link #close()} has already been called
     */
    protected final void requireOpen() {
        if (closed.get()) {
            throw new NativeResourceClosedException(
                getClass().getSimpleName() + " is closed");
        }
    }

    /**
     * Returns whether this resource has been closed.
     *
     * @return {@code true} if {@link #close()} has been called at least once
     */
    public final boolean isClosed() {
        return closed.get();
    }
}
