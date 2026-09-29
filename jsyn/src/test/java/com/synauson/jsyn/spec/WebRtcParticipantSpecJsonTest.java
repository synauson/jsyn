package com.synauson.jsyn.spec;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The JSON the native runtime parses for a WebRTC participant: names and omission of unset options. */
class WebRtcParticipantSpecJsonTest {

    private static JsonObject json(WebRtcParticipantSpec spec) {
        // The same Gson configuration Conference.addWebRtcParticipant uses.
        return JsonParser.parseString(new Gson().toJson(spec)).getAsJsonObject();
    }

    @Test
    void unsetOptionsAreOmittedSoTheRuntimeDefaultsApply() {
        JsonObject o = json(WebRtcParticipantSpec.builder()
            .participantId("p").sdpOffer("v=0").build());
        assertEquals(List.of("participant_id", "sdp_offer"), List.copyOf(o.keySet()));
    }

    @Test
    void everyOptionUsesTheNativeFieldName() {
        JsonObject o = json(WebRtcParticipantSpec.builder()
            .participantId("p")
            .sdpOffer("v=0")
            .stunServer("")
            .jitterBufferMs(0)
            .turnServers(List.of("turn://u:p@turn.example:3478?transport=udp"))
            .iceTransportPolicy(IceTransportPolicy.RELAY)
            .icePortRange(40_000, 40_099)
            .opusBitrate(24_000)
            .opusDtx(false)
            .opusInbandFec(true)
            .opusPacketLossPercentage(15)
            .build());
        assertEquals("", o.get("stun_server").getAsString());
        assertEquals(0, o.get("jitter_buffer_ms").getAsInt());
        assertEquals("turn://u:p@turn.example:3478?transport=udp",
            o.getAsJsonArray("turn_servers").get(0).getAsString());
        assertEquals("relay", o.get("ice_transport_policy").getAsString());
        assertEquals(40_000, o.get("ice_port_min").getAsInt());
        assertEquals(40_099, o.get("ice_port_max").getAsInt());
        assertEquals(24_000, o.get("opus_bitrate").getAsInt());
        assertFalse(o.get("opus_dtx").getAsBoolean());
        assertTrue(o.get("opus_inband_fec").getAsBoolean());
        assertEquals(15, o.get("opus_packet_loss_percentage").getAsInt());
    }

    @Test
    void allPolicySerializesLowercase() {
        JsonObject o = json(WebRtcParticipantSpec.builder()
            .participantId("p").sdpOffer("v=0").iceTransportPolicy(IceTransportPolicy.ALL).build());
        assertEquals("all", o.get("ice_transport_policy").getAsString());
    }

    @Test
    void turnServersAreCopiedAtBuildTime() {
        List<String> uris = new java.util.ArrayList<>(List.of("turns://u:p@a"));
        WebRtcParticipantSpec spec = WebRtcParticipantSpec.builder()
            .participantId("p").sdpOffer("v=0").turnServers(uris).build();
        uris.add("turns://u:p@b");
        assertEquals(List.of("turns://u:p@a"), spec.turnServers);
        assertThrows(UnsupportedOperationException.class, () -> spec.turnServers.add("x"));
    }
}
