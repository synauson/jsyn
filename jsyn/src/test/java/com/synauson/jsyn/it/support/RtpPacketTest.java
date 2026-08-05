package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RtpPacketTest {

    @Test
    void buildAndParseRoundTrips() {
        byte[] payload = {1, 2, 3, 4};
        byte[] wire = RtpPacket.build(0, true, 1234, 96000L, 0xCAFEBABEL, payload);

        RtpPacket parsed = RtpPacket.parse(wire, wire.length);

        assertEquals(0, parsed.payloadType);
        assertEquals(1234, parsed.sequenceNumber);
        assertEquals(96000L, parsed.timestamp);
        assertEquals(0xCAFEBABEL, parsed.ssrc);
        assertArrayEquals(payload, parsed.payload);
    }

    @Test
    void markerBitIsEncodedSeparatelyFromPayloadType() {
        byte[] noMarker = RtpPacket.build(101, false, 1, 0L, 1L, new byte[]{9});
        byte[] withMarker = RtpPacket.build(101, true, 1, 0L, 1L, new byte[]{9});

        assertEquals(101, RtpPacket.parse(noMarker, noMarker.length).payloadType);
        assertEquals(101, RtpPacket.parse(withMarker, withMarker.length).payloadType);
        // Marker bit lives in byte[1] bit 7; payload type must not be corrupted by it.
        assertEquals((byte) 0x65, noMarker[1]);       // 0110 0101 = marker 0, PT 101
        assertEquals((byte) 0xE5, withMarker[1]);     // 1110 0101 = marker 1, PT 101
    }

    @Test
    void parseRejectsPacketShorterThanRtpHeader() {
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.parse(new byte[]{1, 2, 3}, 3));
    }

    @Test
    void multiByteFieldsAreEncodedInNetworkByteOrder() {
        byte[] wire = RtpPacket.build(0, false, 0x0102, 0x03040506L, 0x0708090AL, new byte[0]);
        // bytes 2-3: sequence number, big-endian
        assertEquals((byte) 0x01, wire[2]);
        assertEquals((byte) 0x02, wire[3]);
        // bytes 4-7: timestamp, big-endian
        assertEquals((byte) 0x03, wire[4]);
        assertEquals((byte) 0x04, wire[5]);
        assertEquals((byte) 0x05, wire[6]);
        assertEquals((byte) 0x06, wire[7]);
        // bytes 8-11: ssrc, big-endian
        assertEquals((byte) 0x07, wire[8]);
        assertEquals((byte) 0x08, wire[9]);
        assertEquals((byte) 0x09, wire[10]);
        assertEquals((byte) 0x0A, wire[11]);
    }

    @Test
    void parseRejectsExtensionBit() {
        byte[] wire = RtpPacket.build(0, false, 1, 0L, 1L, new byte[]{9});
        wire[0] |= 0x10; // set extension bit
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.parse(wire, wire.length));
    }
}
