package com.synauson.jsyn;

import com.synauson.jsyn.spec.IceTransportPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** WebRtcStats parses the JSON the native runtime returns, with and without effective options. */
class WebRtcStatsTest {

    @Test
    void parsesEffectiveOptions() {
        WebRtcStats s = WebRtcStats.fromJson("{"
            + "\"participant_id\":\"p\",\"ice_connection_state\":\"connected\","
            + "\"dtls_state\":\"connected\",\"packets_received\":1,\"packets_lost\":0,"
            + "\"packets_sent\":2,\"bytes_received\":3,\"bytes_sent\":4,\"jitter_ns\":0,"
            + "\"rtt_ms\":0,\"effective_options\":{"
            + "\"stun_server\":\"stun://10.0.0.1:3478\","
            + "\"turn_servers\":[\"turn://***@10.0.0.1:3478?transport=udp\"],"
            + "\"ice_transport_policy\":\"relay\",\"ice_port_min\":40000,\"ice_port_max\":40099,"
            + "\"jitter_buffer_ms\":150,\"opus_bitrate\":24000,\"opus_dtx\":false,"
            + "\"opus_inband_fec\":true,\"opus_packet_loss_percentage\":10}}");
        WebRtcEffectiveOptions e = s.effectiveOptions;
        assertNotNull(e);
        assertEquals("stun://10.0.0.1:3478", e.stunServer);
        assertEquals(List.of("turn://***@10.0.0.1:3478?transport=udp"), e.turnServers);
        assertEquals(IceTransportPolicy.RELAY, e.iceTransportPolicy);
        assertEquals(40_000, e.icePortMin);
        assertEquals(40_099, e.icePortMax);
        assertEquals(150, e.jitterBufferMs);
        assertEquals(24_000, e.opusBitrate);
        assertFalse(e.opusDtx);
        assertTrue(e.opusInbandFec);
        assertEquals(10, e.opusPacketLossPercentage);
    }

    @Test
    void anUnrestrictedPortRangeIsNull() {
        WebRtcStats s = WebRtcStats.fromJson("{\"participant_id\":\"p\",\"effective_options\":{"
            + "\"ice_transport_policy\":\"all\",\"ice_port_min\":null,\"ice_port_max\":null}}");
        assertNotNull(s.effectiveOptions);
        assertNull(s.effectiveOptions.icePortMin);
        assertEquals(IceTransportPolicy.ALL, s.effectiveOptions.iceTransportPolicy);
    }

    @Test
    void olderRuntimesReportNoEffectiveOptions() {
        WebRtcStats s = WebRtcStats.fromJson("{\"participant_id\":\"p\",\"packets_sent\":5}");
        assertNull(s.effectiveOptions);
        assertEquals(5, s.packetsSent);
    }
}
