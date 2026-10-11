package com.synauson.jsyn;

import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.exception.InvalidArgumentException;

/**
 * Where a voice-agent event subscription starts, and its heartbeat interval.
 *
 * <p>{@link #defaults()} starts with every event the stream still keeps (its last 512),
 * then live ones. {@link #resumeAfter(long, long)} continues a stream you were reading:
 * pass the {@code streamId} and {@code seq} of the last stored event you processed.
 * Immutable; the {@code with*} methods return a copy.
 *
 * @since 1.6.0
 */
public final class AgentStreamOptions {
    /** Default heartbeat interval in ms; the engine clamps a requested one to 100..30000. */
    public static final int DEFAULT_HEARTBEAT_MS = 1_000;

    private final long afterSeq;
    private final long streamId;
    private final int heartbeatMs;

    private AgentStreamOptions(long afterSeq, long streamId, int heartbeatMs) {
        this.afterSeq = afterSeq;
        this.streamId = streamId;
        this.heartbeatMs = heartbeatMs;
    }

    /**
     * No cursor (every kept event, then live ones) and the default heartbeat.
     *
     * @return the defaults
     */
    public static AgentStreamOptions defaults() {
        return new AgentStreamOptions(-1, -1, 0);
    }

    /**
     * Resume a stream after the event with {@code seq} on stream {@code streamId}.
     *
     * @param streamId the stream the cursor came from ({@link AgentEvent#streamId})
     * @param seq      the last stored event processed ({@link AgentEvent#seq}), 0 or more
     * @return options that resume there
     * @throws InvalidArgumentException if either is negative
     */
    public static AgentStreamOptions resumeAfter(long streamId, long seq) {
        if (streamId < 0 || seq < 0) {
            throw new InvalidArgumentException("streamId and seq must be 0 or more");
        }
        return new AgentStreamOptions(seq, streamId, 0);
    }

    /**
     * Resume after {@code event}, the last stored event processed.
     *
     * @param event a stored event ({@link AgentEvent#isStored()})
     * @return options that resume after it
     */
    public static AgentStreamOptions resumeAfter(AgentEvent event) {
        return resumeAfter(event.streamId, event.seq);
    }

    /**
     * A copy with this heartbeat interval: a {@link AgentEvent.Heartbeat} comes whenever
     * nothing else did for this long. The engine clamps it to 100..30000 ms.
     *
     * @param heartbeatMs the interval in ms, more than 0
     * @return the copy
     * @throws InvalidArgumentException if {@code heartbeatMs} is not positive
     */
    public AgentStreamOptions withHeartbeatMs(int heartbeatMs) {
        if (heartbeatMs <= 0) {
            throw new InvalidArgumentException("heartbeatMs must be more than 0");
        }
        return new AgentStreamOptions(afterSeq, streamId, heartbeatMs);
    }

    /**
     * The cursor, or -1 for none.
     *
     * @return the seq to resume after, or -1
     */
    public long afterSeq() { return afterSeq; }

    /**
     * The cursor's stream, or -1 for any.
     *
     * @return the stream id, or -1
     */
    public long streamId() { return streamId; }

    /**
     * The heartbeat interval, or 0 for the engine's default ({@value #DEFAULT_HEARTBEAT_MS}).
     *
     * @return the interval in ms, or 0
     */
    public int heartbeatMs() { return heartbeatMs; }
}
