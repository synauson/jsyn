package com.synauson.jsyn.spec;

import com.google.gson.annotations.SerializedName;

/**
 * Which ICE candidates a WebRTC participant may use.
 *
 * <p>Serializes to the lowercase names the native runtime reads ({@code "all"},
 * {@code "relay"}).
 *
 * @since 1.5.0
 */
public enum IceTransportPolicy {
    /** Host, server-reflexive and relayed candidates. */
    @SerializedName("all")
    ALL,

    /**
     * Relayed (TURN) candidates only, so the peer never sees the runtime's own addresses.
     * Needs at least one TURN server.
     */
    @SerializedName("relay")
    RELAY
}
