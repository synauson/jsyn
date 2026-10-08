package com.synauson.jsyn.participant;

import com.synauson.jsyn.internal.Args;

/**
 * Handle to a file participant returned by
 * {@link com.synauson.jsyn.participant.Conference#addFileParticipant}.
 *
 * <p>File participants have no additional control surface beyond identity; use
 * {@link Conference#streamFileEvents} to receive playback events and
 * {@link Conference#removeParticipant} to tear them down.
 *
 * @since 0.1.0
 */
public final class FileParticipantHandle {
    private final String participantId;

    /**
     * Construct a file participant handle. Invoked by
     * {@link Conference#addFileParticipant} after the native call returns; rarely
     * constructed from application code.
     *
     * @param participantId the participant identifier; non-null
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code participantId} is null
     */
    public FileParticipantHandle(String participantId) {
        this.participantId = Args.notNull(participantId, "participantId");
    }

    /**
     * Returns the participant's assigned identifier.
     *
     * @return the participant ID
     */
    public String id() { return participantId; }
}
