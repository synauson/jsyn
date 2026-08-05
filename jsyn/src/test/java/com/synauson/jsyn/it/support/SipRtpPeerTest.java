package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
class SipRtpPeerTest {

    @Test
    void sendAudioFrameRejectsWrongSizedFrame() throws Exception {
        try (SipRtpPeer peer = new SipRtpPeer()) {
            peer.setTarget("127.0.0.1", peer.localPort());
            assertThrows(IllegalArgumentException.class, () -> peer.sendAudioFrame(new byte[100]));
        }
    }

    @Test
    void sendBeforeSetTargetThrows() throws Exception {
        try (SipRtpPeer peer = new SipRtpPeer()) {
            assertThrows(IllegalStateException.class, () -> peer.sendAudioFrame(new byte[320]));
        }
    }

    @Test
    void twoPeersCanExchangeAudioFrames() throws Exception {
        try (SipRtpPeer a = new SipRtpPeer();
             SipRtpPeer b = new SipRtpPeer()) {
            a.setTarget("127.0.0.1", b.localPort());

            byte[] frame = new byte[320];
            for (int i = 0; i < 320; i++) frame[i] = (byte) i;
            a.sendAudioFrame(frame);

            long deadline = System.currentTimeMillis() + 5000;
            List<RtpPacket> captured = List.of();
            while (System.currentTimeMillis() < deadline && captured.isEmpty()) {
                captured = b.capturedPackets();
                if (captured.isEmpty()) Thread.sleep(50);
            }

            assertFalse(captured.isEmpty(), "peer b should have captured the frame peer a sent");
            assertEquals(0, captured.get(0).payloadType, "PCMU payload type is 0");
        }
    }

    @Test
    void sendDtmfEventFirstPacketCarriesMarkerBit() throws Exception {
        try (SipRtpPeer a = new SipRtpPeer();
             SipRtpPeer b = new SipRtpPeer()) {
            a.setTarget("127.0.0.1", b.localPort());

            a.sendDtmfEvent(5, 150, 101);

            long deadline = System.currentTimeMillis() + 5000;
            List<RtpPacket> captured = List.of();
            while (System.currentTimeMillis() < deadline && captured.size() < 2) {
                captured = b.capturedPackets();
                if (captured.size() < 2) Thread.sleep(50);
            }

            assertTrue(captured.size() >= 2, "a real DTMF event sends multiple packets");
            for (RtpPacket p : captured) {
                assertEquals(101, p.payloadType);
                assertEquals(5, p.payload[0] & 0xFF, "event number must be 5");
            }
            // Last packet must have the end-of-event bit (0x80) set in payload byte 1.
            RtpPacket last = captured.get(captured.size() - 1);
            assertTrue((last.payload[1] & 0x80) != 0, "final packet must set the end-of-event bit");
        }
    }
}
