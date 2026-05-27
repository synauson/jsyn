package com.synauson.jsyn.participant;

import java.util.Objects;

/**
 * Handle to a recording participant returned by
 * {@link com.synauson.jsyn.participant.Conference#addRecordingParticipant}.
 *
 * <p>Recording participants have no additional control surface beyond identity;
 * remove the participant via {@link Conference#removeParticipant} to close the WAV
 * file and flush the trailing buffer to disk.
 *
 * @since 0.1.0
 */
public final class RecordingParticipantHandle {
    private final String participantId;

    /**
     * Construct a recording participant handle. Invoked by
     * {@link Conference#addRecordingParticipant} after the native call returns;
     * rarely constructed from application code.
     *
     * @param participantId the participant identifier; non-null
     * @throws NullPointerException if {@code participantId} is null
     */
    public RecordingParticipantHandle(String participantId) {
        this.participantId = Objects.requireNonNull(participantId, "participantId");
    }

    /**
     * Returns the participant's assigned identifier.
     *
     * @return the participant ID
     */
    public String id() { return participantId; }
}
