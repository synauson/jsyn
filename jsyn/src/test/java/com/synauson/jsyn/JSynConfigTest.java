package com.synauson.jsyn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JSynConfigTest {
    @Test
    void requiresModelsDir() {
        // modelsDir is enforced via Objects.requireNonNull which throws NullPointerException
        assertThrows(NullPointerException.class, () -> JSynConfig.builder().build());
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
