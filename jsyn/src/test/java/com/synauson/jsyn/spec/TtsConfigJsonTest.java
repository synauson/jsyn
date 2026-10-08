package com.synauson.jsyn.spec;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The JSON the native runtime parses for a participant's speaker: the {@code tts} key and
 * its snake_case fields, on every spec that takes one.
 */
class TtsConfigJsonTest {

    private static JsonObject json(Object spec) {
        // The same Gson configuration Conference uses for every spec.
        return JsonParser.parseString(new Gson().toJson(spec)).getAsJsonObject();
    }

    private static final TtsConfig FULL =
        TtsConfig.defaults().withVoice("en-us-m1").withSpeed(1.5f).withSpeakerId("bot-voice");

    @Test
    void ttsIsSentUnderItsNativeNames() {
        JsonObject[] specs = {
            json(FileParticipantSpec.builder().id("p").uri("file:///a.wav").tts(FULL).build()),
            json(NativeParticipantSpec.builder()
                .format(com.synauson.jsyn.NativeAudioFormat.PCM_S16LE16K_MONO).tts(FULL).build()),
            json(WebRtcParticipantSpec.builder().participantId("p").sdpOffer("v=0").tts(FULL).build()),
            json(SipParticipantSpec.builder().participantId("p").remoteIp("127.0.0.1")
                .remoteRtpPort(5004).codec("PCMU").tts(FULL).build()),
            json(SipConnectionSpec.builder().participantId("p")
                .remote(SipRemoteMedia.builder().remoteIp("127.0.0.1").remoteRtpPort(5004)
                    .codec("PCMU").build())
                .tts(FULL).build()),
        };
        for (JsonObject spec : specs) {
            JsonObject tts = spec.getAsJsonObject("tts");
            assertNotNull(tts, spec.toString());
            assertEquals("en-us-m1", tts.get("voice").getAsString());
            assertEquals(1.5f, tts.get("speed").getAsFloat(), 1e-6);
            assertEquals("bot-voice", tts.get("speaker_id").getAsString());
            assertEquals(3, tts.size(), tts.toString());
        }
    }

    @Test
    void defaultsLeaveEverythingToTheEngine() {
        JsonObject native_ = json(NativeParticipantSpec.builder()
            .format(com.synauson.jsyn.NativeAudioFormat.PCM_S16LE16K_MONO)
            .tts(TtsConfig.defaults())
            .build());
        assertEquals(0, native_.getAsJsonObject("tts").size(), native_.toString());
    }

    @Test
    void noTtsMeansNoKey() {
        JsonObject file = json(FileParticipantSpec.builder().id("p").uri("file:///a.wav").build());
        assertFalse(file.has("tts"), file.toString());
    }
}
