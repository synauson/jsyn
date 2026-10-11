package com.synauson.jsyn;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Immutable point-in-time state snapshot of a conference.
 *
 * <p>Deserialized from the JSON returned by {@code NativeBridge.getConferenceState}.
 * JSON field names match the Rust {@code ConferenceState} serde output (snake_case).
 *
 * <p>All fields are populated by Gson and are effectively immutable; do not mutate the
 * {@link #participants} list.
 *
 * @since 0.1.0
 */
public final class ConferenceState {
    /** Conference identifier this snapshot describes. */
    @SerializedName("conference_id")
    public final String conferenceId;

    /** Summary of every participant currently in the conference, in registration order. */
    public final List<ParticipantInfo> participants;

    /** Unix epoch milliseconds when the conference was started. */
    @SerializedName("created_at_unix_ms")
    public final long createdAtUnixMs;

    /** Required for Gson; not for direct use. */
    private ConferenceState() {
        this.conferenceId = null;
        this.participants = null;
        this.createdAtUnixMs = 0;
    }

    /**
     * Deserialize a conference state from the JSON string returned by the native bridge.
     *
     * @param json JSON string previously returned by {@code NativeBridge.getConferenceState}
     * @return the deserialised state
     */
    public static ConferenceState fromJson(String json) {
        return new Gson().fromJson(json, ConferenceState.class);
    }

    /**
     * Per-participant summary within a {@link ConferenceState}.
     *
     * @since 0.1.0
     */
    public static final class ParticipantInfo {
        /** Participant identifier. */
        @SerializedName("participant_id")
        public final String participantId;

        /**
         * Participant kind tag: one of {@code "file"}, {@code "recording"}, {@code "sip"},
         * {@code "webrtc"}, or {@code "native"}.
         */
        public final String kind;

        private ParticipantInfo() {
            this.participantId = null;
            this.kind = null;
        }
    }
}
