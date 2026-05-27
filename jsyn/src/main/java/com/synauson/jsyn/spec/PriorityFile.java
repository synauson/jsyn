package com.synauson.jsyn.spec;

import java.util.Objects;

/**
 * A URI for priority audio file injection.
 *
 * <p>Priority files are played to a specific participant via the input-selector,
 * overriding their normal audio source until all priority files complete.
 *
 * @since 0.1.0
 */
public final class PriorityFile {
    /** GStreamer-compatible URI (e.g. {@code file:///path/to/audio.wav}). */
    public final String uri;

    /**
     * Construct a priority file entry.
     *
     * @param uri GStreamer-compatible URI; non-null
     * @throws NullPointerException if {@code uri} is null
     */
    public PriorityFile(String uri) {
        this.uri = Objects.requireNonNull(uri, "uri");
    }
}
