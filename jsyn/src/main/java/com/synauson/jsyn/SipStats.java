package com.synauson.jsyn;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * RTP quality statistics for a SIP participant.
 *
 * <p>Deserialized from the JSON returned by {@code NativeBridge.getSipStats}.
 * JSON field names match the Rust {@code SipStats} serde output (snake_case).
 * Use for in-call quality monitoring and post-call analytics.
 *
 * @since 0.1.0
 */
public final class SipStats {
    /** Participant identifier these stats describe. */
    @SerializedName("participant_id")
    public final String participantId;

    /** Total RTP packets received across all SSRCs. */
    @SerializedName("packets_received")
    public final long packetsReceived;

    /** Total RTP packets reported lost by the receiver report. */
    @SerializedName("packets_lost")
    public final long packetsLost;

    /** Average inter-arrival jitter in nanoseconds across active SSRCs. */
    @SerializedName("avg_jitter_ns")
    public final long avgJitterNs;

    /** Round-trip time in milliseconds, derived from RTCP sender/receiver reports. */
    @SerializedName("rtt_ms")
    public final long rttMs;

    /** Number of distinct SSRCs currently receiving RTP traffic. */
    @SerializedName("active_ssrc_count")
    public final int activeSsrcCount;

    private SipStats() {
        this.participantId = null;
        this.packetsReceived = 0;
        this.packetsLost = 0;
        this.avgJitterNs = 0;
        this.rttMs = 0;
        this.activeSsrcCount = 0;
    }

    /**
     * Deserialize SIP stats from the JSON string returned by the native bridge.
     *
     * @param json JSON string previously returned by {@code NativeBridge.getSipStats}
     * @return the deserialised stats snapshot
     */
    public static SipStats fromJson(String json) {
        return new Gson().fromJson(json, SipStats.class);
    }
}
