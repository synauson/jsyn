package com.synauson.jsyn.spec;

import com.synauson.jsyn.internal.Args;
import org.jspecify.annotations.Nullable;

/**
 * Spec for connecting a SIP participant reserved with
 * {@link com.synauson.jsyn.participant.Conference#reserveSipParticipant}, once the peer's SDP
 * answer is in.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code SipConnectionJson}
 * ({@code serde(rename_all = "camelCase")}).
 *
 * @since 1.2.0
 * @see com.synauson.jsyn.participant.Conference#connectSipParticipant(SipConnectionSpec)
 */
public final class SipConnectionSpec {
    /** The reserved participant's identifier. */
    public final String participantId;

    /** The media the peer's SDP answer negotiated. */
    public final SipRemoteMedia remote;

    /** Optional VAD configuration; {@code null} disables VAD detection. */
    public final @Nullable VadConfig vad;

    /**
     * Optional TurnDetection configuration; {@code null} disables TurnDetection detection. Needs
     * {@link #vad} on the same participant, whose speech ends it decides on: turn detection
     * without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     */
    public final @Nullable TurnDetectionConfig turnDetection;

    /**
     * Optional streaming speech-to-text; {@code null} disables it. Needs
     * {@link #turnDetection} on the same participant: STT without it is refused with an
     * {@link com.synauson.jsyn.exception.InvalidArgumentException}.
     *
     * @since 1.6.0
     */
    public final @Nullable SttConfig stt;

    private SipConnectionSpec(Builder b) {
        this.participantId = Args.notNull(b.participantId, "participantId");
        this.remote = Args.notNull(b.remote, "remote");
        this.vad = b.vad;
        this.turnDetection = b.turnDetection;
        this.stt = b.stt;
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link SipConnectionSpec}. {@code participantId} and {@code remote}
     * are required.
     *
     * @since 1.2.0
     */
    public static final class Builder {
        private @Nullable String participantId;
        private @Nullable SipRemoteMedia remote;
        private @Nullable VadConfig vad;
        private @Nullable TurnDetectionConfig turnDetection;
        private @Nullable SttConfig stt;

        /**
         * Set the reserved participant's ID. Required.
         *
         * @param id participant identifier used at reservation; non-null
         * @return this builder
         */
        public Builder participantId(String id) { this.participantId = id; return this; }

        /**
         * Set the peer's negotiated media. Required.
         *
         * @param remote the media from the peer's SDP answer; non-null
         * @return this builder
         */
        public Builder remote(SipRemoteMedia remote) { this.remote = remote; return this; }

        /**
         * Enable VAD detection on this participant's audio stream.
         *
         * @param vad VAD configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder vad(@Nullable VadConfig vad) { this.vad = vad; return this; }

        /**
         * Enable TurnDetection detection on this participant's audio stream.
         * Needs {@link #vad}, whose speech ends it decides on.
         *
         * @param st TurnDetection configuration, or {@code null} to disable
         * @return this builder
         */
        public Builder turnDetection(@Nullable TurnDetectionConfig st) { this.turnDetection = st; return this; }

        /**
         * Enable streaming speech-to-text on this participant's audio; read it with
         * {@link com.synauson.jsyn.participant.Conference#streamTranscriptEvents}.
         * Needs {@link #turnDetection}, whose turn ends close each turn's transcript.
         *
         * @param stt STT configuration, or {@code null} to disable
         * @return this builder
         * @since 1.6.0
         */
        public Builder stt(@Nullable SttConfig stt) { this.stt = stt; return this; }

        /**
         * Materialise an immutable {@link SipConnectionSpec}.
         *
         * @return the configured spec
         * @throws com.synauson.jsyn.exception.InvalidArgumentException naming every required
         *         field that is missing
         */
        public SipConnectionSpec build() {
            Args.required("SipConnectionSpec")
                .field("participantId", participantId)
                .field("remote", remote)
                .validate();
            return new SipConnectionSpec(this);
        }
    }
}
