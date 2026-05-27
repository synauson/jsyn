package com.synauson.jsyn;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * RTP/ICE quality statistics for a WebRTC participant.
 *
 * <p>Deserialized from the JSON returned by {@code NativeBridge.getWebRtcStats}.
 * JSON field names match the Rust {@code WebRtcStats} serde output (snake_case).
 * Use for in-call quality monitoring and post-call analytics.
 *
 * @since 0.1.0
 */
public final class WebRtcStats {
    /** Participant identifier these stats describe. */
    @SerializedName("participant_id")
    public final String participantId;

    /**
     * Current ICE connection state as a lowercase string
     * ({@code "new"}, {@code "checking"}, {@code "connected"}, {@code "completed"},
     * {@code "failed"}, {@code "disconnected"}, {@code "closed"}).
     */
    @SerializedName("ice_connection_state")
    public final String iceConnectionState;

    /**
     * Current DTLS handshake state as a lowercase string
     * ({@code "new"}, {@code "connecting"}, {@code "connected"}, {@code "closed"},
     * {@code "failed"}).
     */
    @SerializedName("dtls_state")
    public final String dtlsState;

    /** Total RTP packets received from the remote peer. */
    @SerializedName("packets_received")
    public final long packetsReceived;

    /** Total RTP packets reported lost by the receiver report. */
    @SerializedName("packets_lost")
    public final long packetsLost;

    /** Total RTP packets sent to the remote peer. */
    @SerializedName("packets_sent")
    public final long packetsSent;

    /** Total bytes received from the remote peer. */
    @SerializedName("bytes_received")
    public final long bytesReceived;

    /** Total bytes sent to the remote peer. */
    @SerializedName("bytes_sent")
    public final long bytesSent;

    /** Inter-arrival jitter in nanoseconds, exponentially smoothed per RFC 3550. */
    @SerializedName("jitter_ns")
    public final long jitterNs;

    /** Round-trip time in milliseconds, derived from RTCP sender/receiver reports. */
    @SerializedName("rtt_ms")
    public final long rttMs;

    private WebRtcStats() {
        this.participantId = null;
        this.iceConnectionState = null;
        this.dtlsState = null;
        this.packetsReceived = 0;
        this.packetsLost = 0;
        this.packetsSent = 0;
        this.bytesReceived = 0;
        this.bytesSent = 0;
        this.jitterNs = 0;
        this.rttMs = 0;
    }

    /**
     * Deserialize WebRTC stats from the JSON string returned by the native bridge.
     *
     * @param json JSON string previously returned by {@code NativeBridge.getWebRtcStats}
     * @return the deserialised stats snapshot
     */
    public static WebRtcStats fromJson(String json) {
        return new Gson().fromJson(json, WebRtcStats.class);
    }
}
