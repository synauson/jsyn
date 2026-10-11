package com.synauson.jsyn.spec;

import com.synauson.jsyn.internal.Args;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code entries} or any of
     *         its elements is null
     */
    public ConnectionMatrix(List<ConnectionEntry> entries) {
        this.entries = copyOf(Args.notNull(entries, "entries"));
    }

    /**
     * Construct a matrix from a varargs sequence of entries.
     *
     * @param entries entries to include
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code entries} or any of
     *         its elements is null
     */
    public ConnectionMatrix(ConnectionEntry... entries) {
        this.entries = copyOf(Arrays.asList(Args.notNull(entries, "entries")));
    }

    private static List<ConnectionEntry> copyOf(List<ConnectionEntry> entries) {
        List<ConnectionEntry> copy = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            copy.add(Args.notNull(entries.get(i), "entries[" + i + "]"));
        }
        return Collections.unmodifiableList(copy);
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
