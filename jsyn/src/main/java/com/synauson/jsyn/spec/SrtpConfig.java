package com.synauson.jsyn.spec;

/**
 * SRTP key material for a SIP participant.
 *
 * <p>Serializes to camelCase to match the Rust {@code SrtpConfigJson}
 * ({@code serde(rename_all = "camelCase")}): {@code ourKey} and {@code theirKey} as
 * byte arrays.
 *
 * <p>Both keys must be 30 bytes (16-byte AES master key + 14-byte master salt) for
 * AES-128-CTR HMAC-SHA1-80. Key material is typically derived from a separate DTLS-SRTP
 * handshake or out-of-band key exchange.
 *
 * @since 0.1.0
 */
public final class SrtpConfig {
    /** 30-byte master key for our send path ({@code srtpenc}). */
    public final byte[] ourKey;

    /** 30-byte master key for the remote's send path ({@code srtpdec}). */
    public final byte[] theirKey;

    /**
     * Construct an SRTP configuration.
     *
     * @param ourKey   30-byte master key for the local send path
     * @param theirKey 30-byte master key for the remote send path
     */
    public SrtpConfig(byte[] ourKey, byte[] theirKey) {
        this.ourKey = ourKey;
        this.theirKey = theirKey;
    }
}
