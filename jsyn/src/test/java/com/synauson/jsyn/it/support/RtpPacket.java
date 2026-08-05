package com.synauson.jsyn.it.support;

/**
 * A single RTP packet (RFC 3550, fixed 12-byte header, no CSRC list, no
 * header extension). Used by {@code SipRtpPeer} to build outgoing packets
 * and decode whatever synauson sends back.
 */
public final class RtpPacket {
    public final int payloadType;
    public final boolean marker;
    public final int sequenceNumber;
    public final long timestamp;
    public final long ssrc;
    public final byte[] payload;

    public RtpPacket(int payloadType, boolean marker, int sequenceNumber, long timestamp, long ssrc,
                      byte[] payload) {
        this.payloadType = payloadType;
        this.marker = marker;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.ssrc = ssrc;
        this.payload = payload;
    }

    /** Parse a raw UDP datagram as an RTP packet. */
    public static RtpPacket parse(byte[] buf, int len) {
        if (len < 12) {
            throw new IllegalArgumentException("packet too short to be RTP: " + len + " bytes");
        }
        if ((buf[0] & 0x20) != 0) {
            throw new IllegalArgumentException("padding bit set: unsupported");
        }
        if ((buf[0] & 0x10) != 0) {
            throw new IllegalArgumentException("extension bit set: unsupported");
        }
        if ((buf[0] & 0x0F) != 0) {
            throw new IllegalArgumentException("CSRC count nonzero: unsupported");
        }
        int payloadType = buf[1] & 0x7F;
        boolean marker = (buf[1] & 0x80) != 0;
        int sequenceNumber = ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
        long timestamp = ((long) (buf[4] & 0xFF) << 24) | ((long) (buf[5] & 0xFF) << 16)
                | ((long) (buf[6] & 0xFF) << 8) | (buf[7] & 0xFF);
        long ssrc = ((long) (buf[8] & 0xFF) << 24) | ((long) (buf[9] & 0xFF) << 16)
                | ((long) (buf[10] & 0xFF) << 8) | (buf[11] & 0xFF);
        byte[] payload = new byte[len - 12];
        System.arraycopy(buf, 12, payload, 0, payload.length);
        return new RtpPacket(payloadType, marker, sequenceNumber, timestamp, ssrc, payload);
    }

    /** Serialize an RTP header + payload ready to send over a socket. */
    public static byte[] build(int payloadType, boolean marker, int sequenceNumber,
                                long timestamp, long ssrc, byte[] payload) {
        byte[] out = new byte[12 + payload.length];
        out[0] = (byte) 0x80; // version=2, padding=0, extension=0, CSRC count=0
        out[1] = (byte) ((marker ? 0x80 : 0x00) | (payloadType & 0x7F));
        out[2] = (byte) ((sequenceNumber >> 8) & 0xFF);
        out[3] = (byte) (sequenceNumber & 0xFF);
        out[4] = (byte) ((timestamp >> 24) & 0xFF);
        out[5] = (byte) ((timestamp >> 16) & 0xFF);
        out[6] = (byte) ((timestamp >> 8) & 0xFF);
        out[7] = (byte) (timestamp & 0xFF);
        out[8] = (byte) ((ssrc >> 24) & 0xFF);
        out[9] = (byte) ((ssrc >> 16) & 0xFF);
        out[10] = (byte) ((ssrc >> 8) & 0xFF);
        out[11] = (byte) (ssrc & 0xFF);
        System.arraycopy(payload, 0, out, 12, payload.length);
        return out;
    }
}
