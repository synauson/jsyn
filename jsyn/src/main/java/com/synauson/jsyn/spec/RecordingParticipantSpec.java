package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * Spec for adding a recording participant.
 *
 * <p>Serializes to the JSON shape expected by the Rust {@code RecordingParticipantSpec}:
 * snake_case field names ({@code participant_id}, {@code source_participant_id},
 * {@code output_path}).
 *
 * <p>A recording participant taps the conference audio for {@code sourceParticipantId}
 * and writes a WAV file to {@code outputPath}. The file is closed when the participant
 * is removed via {@link com.synauson.jsyn.participant.Conference#removeParticipant}.
 *
 * @since 0.1.0
 */
public final class RecordingParticipantSpec {
    /** Participant identifier for the recorder itself. */
    @SerializedName("participant_id")
    public final String id;

    /** Identifier of the participant whose audio is being recorded. */
    @SerializedName("source_participant_id")
    public final String sourceParticipantId;

    /** Filesystem path where the WAV file will be written. */
    @SerializedName("output_path")
    public final String outputPath;

    private RecordingParticipantSpec(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.sourceParticipantId = Objects.requireNonNull(b.sourceParticipantId, "sourceParticipantId");
        this.outputPath = Objects.requireNonNull(b.outputPath, "outputPath");
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return a fresh builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link RecordingParticipantSpec}. All three fields are required.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private String id;
        private String sourceParticipantId;
        private String outputPath;

        /**
         * Set the recorder's participant ID. Required.
         *
         * @param id participant identifier for the recorder; non-null
         * @return this builder
         */
        public Builder id(String id) { this.id = id; return this; }

        /**
         * Set the ID of the participant whose audio is to be recorded. Required.
         *
         * @param sid source participant identifier; non-null
         * @return this builder
         */
        public Builder sourceParticipantId(String sid) { this.sourceParticipantId = sid; return this; }

        /**
         * Set the filesystem path for the output WAV file. Required.
         *
         * @param path absolute or process-relative output path; non-null
         * @return this builder
         */
        public Builder outputPath(String path) { this.outputPath = path; return this; }

        /**
         * Materialise an immutable {@link RecordingParticipantSpec}.
         *
         * @return the configured spec
         * @throws NullPointerException if any required field is null
         */
        public RecordingParticipantSpec build() { return new RecordingParticipantSpec(this); }
    }
}
