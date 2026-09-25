package com.synauson.jsyn;

import com.synauson.jsyn.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JSynConfigTest {
    @Test
    void requiresModelsDir() {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class,
            () -> JSynConfig.builder().build());
        assertEquals("JSynConfig requires modelsDir", e.getMessage());
    }

    @Test
    void reportsEveryMissingField() {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class,
            () -> JSynConfig.builder().webrtcStunServer(null).build());
        assertEquals("JSynConfig requires modelsDir, webrtcStunServer", e.getMessage());
    }

    @Test
    void maxConferencesDefaultsToUnlimited() {
        JSynConfig cfg = JSynConfig.builder().modelsDir("/models").build();
        assertNull(cfg.maxConferences);
        assertNull(cfg.maxParticipantsPerConference);
    }

    @Test
    void buildsWithModelsDir() {
        JSynConfig cfg = JSynConfig.builder()
            .modelsDir("/some/path")
            .build();
        assertNotNull(cfg);
        assertEquals("/some/path", cfg.modelsDir);
    }

    @Test
    void defaultsAreReasonable() {
        JSynConfig cfg = JSynConfig.builder()
            .modelsDir("/models")
            .build();
        assertEquals(10000, cfg.rtpPortMin);
        assertEquals(20000, cfg.rtpPortMax);
        assertEquals(200, cfg.rtpJitterBufferMs);
        assertNotNull(cfg.webrtcStunServer);
    }

    @Test
    void toJsonContainsModelsDir() {
        JSynConfig cfg = JSynConfig.builder()
            .modelsDir("/opt/models")
            .build();
        String json = cfg.toJson();
        assertNotNull(json);
        assertTrue(json.contains("modelsDir") || json.contains("/opt/models"),
            "JSON should contain modelsDir value");
    }
}
