package com.synauson.jsyn;

import com.synauson.jsyn.internal.NativeBridge;
import com.synauson.jsyn.internal.NativeResource;

/**
 * A handle to an active event stream subscription.
 *
 * <p>Closing a {@code Subscription} cancels the subscription on the native side by
 * calling {@code NativeBridge.unsubscribe(long)}. The associated
 * {@link EventStreamObserver} will no longer receive events after {@link #close()} returns.
 *
 * <p>Use in try-with-resources to guarantee cancellation:
 * <pre>{@code
 * try (Subscription sub = conf.streamVadEvents(pid, observer)) {
 *     // ... receive events ...
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class Subscription extends NativeResource {

    private final long subId;

    /**
     * Construct a subscription handle wrapping a native subscription ID.
     *
     * <p>Called by the {@code Conference} stream* methods after the native bridge
     * returns a fresh subscription ID. Application code typically does not call this
     * constructor directly.
     *
     * @param subId opaque subscription ID returned by one of the native
     *              {@code subscribe*} methods on {@link NativeBridge}
     */
    public Subscription(long subId) {
        super(() -> NativeBridge.unsubscribe(subId));
        this.subId = subId;
    }

    /**
     * Returns the opaque subscription ID assigned by the native runtime.
     *
     * @return the subscription ID
     */
    public long id() { return subId; }
}
