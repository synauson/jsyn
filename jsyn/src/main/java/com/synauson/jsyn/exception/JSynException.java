package com.synauson.jsyn.exception;

/**
 * Base class for all jsyn runtime exceptions.
 *
 * <p>Subclasses map 1-to-1 to {@code CoreError} variants on the Rust side, allowing
 * callers to catch specific error categories without inspecting message strings.
 *
 * <p>All jsyn exceptions are unchecked ({@link RuntimeException}) so application code is
 * not required to declare them, but well-written callers should handle the relevant
 * subclass for each operation (see the {@code @throws} clauses on individual methods).
 *
 * @since 0.1.0
 */
public class JSynException extends RuntimeException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg human-readable error description, propagated from the native layer
     */
    public JSynException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   human-readable error description
     * @param cause the underlying cause, typically another {@link Throwable} from native code
     */
    public JSynException(String msg, Throwable cause) { super(msg, cause); }
}
