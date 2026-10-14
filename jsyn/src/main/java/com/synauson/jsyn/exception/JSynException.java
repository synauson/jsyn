package com.synauson.jsyn.exception;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

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
    /** A reason as the engine writes one: upper-case words joined by underscores. */
    private static final Pattern REASON = Pattern.compile("^([A-Z][A-Z0-9_]*[A-Z0-9]): ");

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

    /**
     * The engine's stable reason for this failure, such as {@code SESSION_LIMIT_REACHED}
     * or {@code LICENSE_SHARE_PENDING}: what to match on, never the message. The engine
     * leads every message with it; {@code null} for an exception jsyn raised itself
     * without one. The reasons are listed in the API's {@code ErrorReason} and on each
     * exception class; new ones may be added.
     *
     * @return the reason, or {@code null}
     * @since 1.7.0
     */
    public @Nullable String reason() {
        String msg = getMessage();
        if (msg == null) {
            return null;
        }
        Matcher m = REASON.matcher(msg);
        return m.find() ? m.group(1) : null;
    }
}
