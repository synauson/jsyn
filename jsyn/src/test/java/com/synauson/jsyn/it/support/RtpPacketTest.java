package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
