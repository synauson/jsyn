package com.synauson.jsyn.spec;

import com.synauson.jsyn.internal.Args;

/**
 * A URI for priority audio file injection.
 *
 * <p>Priority files play whole, in order, in place of a file participant's own file,
 * which resumes where it paused once they have all played. See
 * {@link com.synauson.jsyn.participant.Conference#addPriorityAudioFiles}.
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code uri} is null
     */
    public PriorityFile(String uri) {
        this.uri = Args.notNull(uri, "uri");
    }
}
