package com.synauson.jsyn;

/**
 * Listener for events emitted by a native participant stream subscription.
 *
 * <p>Implementations receive typed events via {@link #onNext}, terminal errors via
 * {@link #onError}, and normal stream completion via {@link #onCompleted}. The default
 * {@link #onError} implementation logs a warning; the default {@link #onCompleted}
 * is a no-op.
 *
 * <p>This is a {@link FunctionalInterface}: callers can provide a lambda for the common
 * case where only {@link #onNext} is needed.
 *
 * @param <T> the event type (e.g. {@code VadEvent}, {@code FileEvent})
 * @since 0.1.0
 */
@FunctionalInterface
public interface EventStreamObserver<T> {

    /**
     * Called on every event from the stream. Invoked from a Rust-owned thread; do not
     * block or throw unchecked exceptions from this method.
     *
     * @param event the next event; never null
     */
    void onNext(T event);

    /**
     * Called when the stream terminates with an error. The default implementation logs
     * a warning via the {@link System.Logger} API.
     *
     * @param t the throwable describing the terminal error
     */
    default void onError(Throwable t) {
        System.getLogger("com.synauson.jsyn")
            .log(System.Logger.Level.WARNING, "stream error", t);
    }

    /**
     * Called when the stream completes normally (e.g. the conference ended). The default
     * implementation is a no-op.
     */
    default void onCompleted() {}
}
