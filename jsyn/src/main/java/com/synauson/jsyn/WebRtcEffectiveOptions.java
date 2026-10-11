package com.synauson.jsyn;

import com.google.gson.annotations.SerializedName;
import com.synauson.jsyn.spec.IceTransportPolicy;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The WebRTC options a live participant runs with, part of {@link WebRtcStats}.
 *
 * <p>The ICE port range, jitter buffer and Opus fields are read back from the participant's
 * media elements, so they show whether an option took effect. The STUN, TURN and policy
 * fields are the resolved values the runtime applied.
 *
 * @since 1.5.0
 */
public final class WebRtcEffectiveOptions {
    /** STUN server URI, or an empty string when STUN is off. */
    @SerializedName("stun_server")
    public final String stunServer;

    /** TURN server URIs with the credentials replaced by {@code ***}. */
    @SerializedName("turn_servers")
    public final List<String> turnServers;

    /** Which ICE candidates the participant may use. */
    @SerializedName("ice_transport_policy")
    public final IceTransportPolicy iceTransportPolicy;

    /** Lowest local ICE port, or {@code null} when the ICE agent may use any port. */
    @SerializedName("ice_port_min")
    public final @Nullable Integer icePortMin;

    /** Highest local ICE port, or {@code null} when the ICE agent may use any port. */
    @SerializedName("ice_port_max")
    public final @Nullable Integer icePortMax;

    /** Receive jitter buffer latency in milliseconds. */
    @SerializedName("jitter_buffer_ms")
    public final int jitterBufferMs;

    /** Bitrate of the Opus audio sent to the participant, in bps. */
    @SerializedName("opus_bitrate")
    public final int opusBitrate;

    /** Whether Opus discontinuous transmission is on for the audio sent. */
    @SerializedName("opus_dtx")
    public final boolean opusDtx;

    /** Whether Opus in-band forward error correction is on for the audio sent. */
    @SerializedName("opus_inband_fec")
    public final boolean opusInbandFec;

    /** Expected packet loss percentage the Opus encoder sizes its FEC for. */
    @SerializedName("opus_packet_loss_percentage")
    public final int opusPacketLossPercentage;

    private WebRtcEffectiveOptions() {
        this.stunServer = "";
        this.turnServers = List.of();
        this.iceTransportPolicy = IceTransportPolicy.ALL;
        this.icePortMin = null;
        this.icePortMax = null;
        this.jitterBufferMs = 0;
        this.opusBitrate = 0;
        this.opusDtx = false;
        this.opusInbandFec = false;
        this.opusPacketLossPercentage = 0;
    }
}
