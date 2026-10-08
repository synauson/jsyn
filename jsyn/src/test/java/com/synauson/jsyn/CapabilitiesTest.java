package com.synauson.jsyn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilitiesTest {
    // The shape the native CapabilitiesReport serializes to (serde, camelCase).
    private static final String JSON = "{"
        + "\"license\":{\"state\":\"licensed\",\"description\":\"license 'free' (Server, file valid until 2026-10-27 16:25 UTC)\","
        + "\"licenseId\":\"f5544dce\",\"name\":\"free\",\"source\":\"server\","
        + "\"fileExpiry\":\"2026-10-27T16:25:22.204+00:00\",\"licenseExpiry\":null},"
        + "\"limitsScope\":\"this instance\",\"overdraft\":0.25,"
        + "\"conferences\":{\"limit\":10,\"inUse\":3},"
        + "\"aiConferences\":{\"limit\":2,\"inUse\":1},"
        + "\"capabilities\":["
        + "{\"code\":\"FEATURE_VAD\",\"entitled\":true,\"streams\":{\"limit\":null,\"inUse\":0}},"
        + "{\"code\":\"FEATURE_TURN_DETECTION\",\"entitled\":true,\"streams\":{\"limit\":4,\"inUse\":2}}],"
        + "\"models\":["
        + "{\"id\":\"sentito-1\",\"version\":\"5\",\"release\":\"5.0.0\",\"state\":\"ready\",\"detail\":null},"
        + "{\"id\":\"fermata-1\",\"version\":\"1.0.0-cpu\",\"release\":\"1.0.0\",\"state\":\"missing\",\"detail\":\"not installed\"}]"
        + "}";

    @Test
    void readsTheNativeReport() {
        Capabilities c = Capabilities.fromJson(JSON);
        assertEquals("licensed", c.license.state);
        assertEquals("free", c.license.name);
        assertNull(c.license.licenseExpiry);
        assertEquals("this instance", c.limitsScope);
        assertEquals(0.25, c.overdraft);
        assertEquals(Integer.valueOf(10), c.conferences.limit);
        assertEquals(3, c.conferences.inUse);
        assertEquals(1, c.aiConferences.inUse);

        assertEquals(2, c.capabilities.size());
        assertNull(c.capabilities.get(0).streams.limit, "null is unlimited");
        assertEquals("FEATURE_TURN_DETECTION", c.capabilities.get(1).code);
        assertEquals(2, c.capabilities.get(1).streams.inUse);

        assertEquals("ready", c.models.get(0).state);
        assertEquals("missing", c.models.get(1).state);
        assertEquals("not installed", c.models.get(1).detail);
    }
}
