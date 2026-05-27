package com.synauson.jsyn.spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Full N-to-M audio routing matrix for a conference.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code ConnectionMatrix}: a
 * single {@code entries} field containing an array of {@link ConnectionEntry}.
 *
 * <p>Passed to {@link com.synauson.jsyn.participant.Conference#updatePartyAudioConnections}.
 * Calling that method replaces the entire matrix: connections present in this matrix are
 * added, those not present are removed.
 *
 * @since 0.1.0
 */
public final class ConnectionMatrix {
    /** Immutable list of every directed connection in the matrix. */
    public final List<ConnectionEntry> entries;

    /**
     * Construct a matrix from a list of entries. The list is defensively copied and wrapped
     * in {@link Collections#unmodifiableList(List)}.
     *
     * @param entries entries to include; non-null
     * @throws NullPointerException if {@code entries} is null
     */
    public ConnectionMatrix(List<ConnectionEntry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(
            Objects.requireNonNull(entries, "entries")));
    }

    /**
     * Construct a matrix from a varargs sequence of entries.
     *
     * @param entries entries to include
     */
    public ConnectionMatrix(ConnectionEntry... entries) {
        this.entries = Collections.unmodifiableList(Arrays.asList(entries));
    }

    /**
     * Returns an empty matrix; applying it removes all existing connections in the
     * conference.
     *
     * @return an empty {@link ConnectionMatrix}
     */
    public static ConnectionMatrix empty() {
        return new ConnectionMatrix(Collections.emptyList());
    }
}
