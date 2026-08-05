package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            assertTrue(captured.get(0).marker, "first packet of a new event must carry the marker bit");
            for (int i = 1; i < captured.size(); i++) {
                assertFalse(captured.get(i).marker, "only the first packet of an event should carry the marker bit");
            }
            // Last packet must have the end-of-event bit (0x80) set in payload byte 1.
            RtpPacket last = captured.get(captured.size() - 1);
            assertTrue((last.payload[1] & 0x80) != 0, "final packet must set the end-of-event bit");
        }
    }

    @Test
    void receiveLoopSurvivesMalformedPacketAndKeepsCapturing() throws Exception {
        try (SipRtpPeer a = new SipRtpPeer();
             SipRtpPeer b = new SipRtpPeer();
             DatagramSocket rawSender = new DatagramSocket()) {
            a.setTarget("127.0.0.1", b.localPort());
            assertNull(b.receiveLoopFailure(), "no failure should be recorded before anything is sent");

            // Too short to be RTP (RtpPacket.parse requires at least 12 bytes) — a real peer
            // could legitimately send something this small (e.g. a keepalive), and it must not
            // silently kill the receive loop.
            byte[] garbage = {1, 2, 3};
            rawSender.send(new DatagramPacket(garbage, garbage.length, InetAddress.getByName("127.0.0.1"),
                    b.localPort()));

            long failureDeadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < failureDeadline && b.receiveLoopFailure() == null) {
                Thread.sleep(50);
            }
            assertNotNull(b.receiveLoopFailure(), "malformed packet should be recorded, not silently dropped");

            byte[] frame = new byte[320];
            for (int i = 0; i < 320; i++) frame[i] = (byte) i;
            a.sendAudioFrame(frame);

            long captureDeadline = System.currentTimeMillis() + 5000;
            List<RtpPacket> captured = List.of();
            while (System.currentTimeMillis() < captureDeadline && captured.isEmpty()) {
                captured = b.capturedPackets();
                if (captured.isEmpty()) Thread.sleep(50);
            }

            assertFalse(captured.isEmpty(), "receive loop must keep capturing valid packets after a malformed one");
            assertEquals(0, captured.get(0).payloadType, "PCMU payload type is 0");
        }
    }
}
