package com.synauson.jsyn.exception;

/**
 * Thrown when a method is called on a {@link com.synauson.jsyn.internal.NativeResource}
 * that has already been closed.
 *
 * <p>This is a defensive guard surfaced by the Java wrapper before delegating to the
 * native layer; the underlying handle is not consulted. Indicates use-after-close in
 * application code, typically because a try-with-resources block has exited or
 * {@code close()} was called explicitly. Not thrown by the native runtime itself.
 *
 * @since 0.1.0
 */
public class NativeResourceClosedException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of the closed resource (typically the class name)
     */
    public NativeResourceClosedException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of the closed resource
     * @param cause the underlying cause
     */
    public NativeResourceClosedException(String msg, Throwable cause) { super(msg, cause); }
}
