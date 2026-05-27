package com.synauson.jsyn.event;

/**
 * Trickle-ICE candidate emitted by a WebRTC participant's GStreamer pipeline.
 *
 * <p>Emitted on the WebRTC ICE candidate stream returned by
 * {@link com.synauson.jsyn.participant.Conference#streamWebRtcIceCandidates}.
 * JNI constructor signature (from {@code synauson-jni/src/jni_cache.rs IceCandidateEventClasses}):
 * {@code (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ)V}.
 *
 * <p>The caller must relay this candidate to the remote peer's browser via the application
 * signaling channel, then optionally add any remote candidates back via
 * {@link com.synauson.jsyn.participant.WebRtcParticipantHandle#addIceCandidate}.
 *
 * @since 0.1.0
 */
public final class IceCandidateEvent {
    /** Conference ID this event was emitted for. */
    public final String conferenceId;
    /** Participant ID this event was emitted for. */
    public final String participantId;
    /** SDP candidate string (e.g. {@code "candidate:..."}). Empty string if {@link #endOfCandidates}. */
    public final String candidate;
    /** SDP m-line index this candidate applies to. */
    public final int sdpMLineIndex;
    /** {@code true} when this is the end-of-candidates sentinel (candidate string is empty). */
    public final boolean endOfCandidates;

    /**
     * Construct an ICE candidate event. Invoked from the JNI layer; rarely called from
     * application code.
     *
     * @param conferenceId    conference identifier
     * @param participantId   participant identifier
     * @param candidate       SDP candidate string, or empty when {@code endOfCandidates} is true
     * @param sdpMLineIndex   SDP m-line index the candidate applies to
     * @param endOfCandidates {@code true} for the end-of-candidates sentinel
     */
    public IceCandidateEvent(String conferenceId, String participantId, String candidate,
                              int sdpMLineIndex, boolean endOfCandidates) {
        this.conferenceId = conferenceId;
        this.participantId = participantId;
        this.candidate = candidate;
        this.sdpMLineIndex = sdpMLineIndex;
        this.endOfCandidates = endOfCandidates;
    }
}
