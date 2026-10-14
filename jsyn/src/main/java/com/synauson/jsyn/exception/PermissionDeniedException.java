package com.synauson.jsyn.exception;

/**
 * Thrown when the license does not cover what was requested. Every license refusal throws
 * this, though the gRPC server sends some as {@code FAILED_PRECONDITION}. The message
 * leads with the reason:
 * <ul>
 *   <li>{@code LICENSE_REJECTED}: the licensing server rejected the license; nothing new
 *       starts.</li>
 *   <li>{@code LICENSE_DISCONNECTED}: no usage report has got through for 7 days; nothing
 *       new starts until one does.</li>
 *   <li>{@code LICENSE_EXPIRED}: a developer key expired; no new conference or
 *       participant starts until Synauson renews it.</li>
 *   <li>{@code NO_LICENSE}: {@code new JSyn} found no license file that still works.</li>
 * </ul>
 * Calls in progress continue through the first three.
 * {@link com.synauson.jsyn.JSyn#capabilities()} shows the license's standing.
 *
 * @since 1.3.0
 */
public class PermissionDeniedException extends JSynException {
    /**
     * Construct a new exception with the given message.
     *
     * @param msg description of what is not permitted
     */
    public PermissionDeniedException(String msg) { super(msg); }

    /**
     * Construct a new exception with the given message and cause.
     *
     * @param msg   description of what is not permitted
     * @param cause the underlying cause
     */
    public PermissionDeniedException(String msg, Throwable cause) { super(msg, cause); }
}
