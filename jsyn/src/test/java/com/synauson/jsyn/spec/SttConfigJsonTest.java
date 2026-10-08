package com.synauson.jsyn.spec;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The JSON the native runtime parses for a participant's STT: the {@code stt} key and its fields. */
class SttConfigJsonTest {

    private static JsonObject json(Object spec) {
        // The same Gson configuration Conference uses for every spec.
        return JsonParser.parseString(new Gson().toJson(spec)).getAsJsonObject();
    }

    @Test
    void sttIsSentUnderItsNativeName() {
        JsonObject file = json(FileParticipantSpec.builder()
            .id("p").uri("file:///a.wav")
            .turnDetection(TurnDetectionConfig.defaults())
            .stt(new SttConfig(500))
            .build());
        assertEquals(500, file.getAsJsonObject("stt").get("turn_drain_ms").getAsInt());

        JsonObject webrtc = json(WebRtcParticipantSpec.builder()
            .participantId("p").sdpOffer("v=0")
            .turnDetection(TurnDetectionConfig.defaults())
            .stt(new SttConfig(0))
            .build());
        assertEquals(0, webrtc.getAsJsonObject("stt").get("turn_drain_ms").getAsInt());
    }

    @Test
    void defaultsLeaveTheDrainToTheEngine() {
        JsonObject native_ = json(NativeParticipantSpec.builder()
            .format(com.synauson.jsyn.NativeAudioFormat.PCM_S16LE16K_MONO)
            .turnDetection(TurnDetectionConfig.defaults())
            .stt(SttConfig.defaults())
            .build());
        assertEquals(0, native_.getAsJsonObject("stt").size(), native_.toString());
    }

    @Test
    void noSttMeansNoKey() {
        JsonObject file = json(FileParticipantSpec.builder().id("p").uri("file:///a.wav").build());
        assertFalse(file.has("stt"), file.toString());
    }
}
