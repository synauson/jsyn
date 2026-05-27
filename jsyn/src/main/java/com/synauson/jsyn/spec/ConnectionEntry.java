package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * A single directed audio connection entry in a {@link ConnectionMatrix}.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code ConnectionEntry}:
 * snake_case field names ({@code source_id}, {@code dest_id}, {@code muted}).
 *
 * <p>Represents a one-way audio link from {@code sourceId} to {@code destId}, optionally
 * pre-muted. Connections are unidirectional; full duplex requires two entries.
 *
 * @since 0.1.0
 */
public final class ConnectionEntry {
    /** Source participant identifier. */
    @SerializedName("source_id")
    public final String sourceId;

    /** Destination participant identifier. */
    @SerializedName("dest_id")
    public final String destId;

    /** Whether audio flowing from source to dest is muted. */
    public final boolean muted;

    /**
     * Construct a connection entry.
     *
     * @param sourceId source participant identifier; non-null
     * @param destId   destination participant identifier; non-null
     * @param muted    {@code true} if the connection is initially muted
     */
    public ConnectionEntry(String sourceId, String destId, boolean muted) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.destId = Objects.requireNonNull(destId, "destId");
        this.muted = muted;
    }

    /**
     * Convenience factory for an unmuted connection.
     *
     * @param sourceId source participant identifier; non-null
     * @param destId   destination participant identifier; non-null
     * @return a new entry with {@code muted=false}
     */
    public static ConnectionEntry connect(String sourceId, String destId) {
        return new ConnectionEntry(sourceId, destId, false);
    }

    /**
     * Convenience factory for a muted connection.
     *
     * @param sourceId source participant identifier; non-null
     * @param destId   destination participant identifier; non-null
     * @return a new entry with {@code muted=true}
     */
    public static ConnectionEntry muted(String sourceId, String destId) {
        return new ConnectionEntry(sourceId, destId, true);
    }
}
