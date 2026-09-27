package com.synauson.jsyn;

import com.synauson.jsyn.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JSynConfigTest {
    @Test
    void buildsWithNoArguments() {
        JSynConfig cfg = JSynConfig.builder().build();
        assertNull(cfg.modelStore, "null selects the default model store");
    }

    @Test
    void reportsEveryMissingField() {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class,
            () -> JSynConfig.builder().webrtcStunServer(null).build());
        assertEquals("JSynConfig requires webrtcStunServer", e.getMessage());
    }

    @Test
    void maxConferencesDefaultsToUnlimited() {
        JSynConfig cfg = JSynConfig.builder().build();
        assertNull(cfg.maxConferences);
        assertNull(cfg.maxParticipantsPerConference);
    }

    @Test
    void buildsWithModelStore() {
        JSynConfig cfg = JSynConfig.builder()
            .modelStore("/some/path")
            .build();
        assertEquals("/some/path", cfg.modelStore);
    }

    @Test
    void nullModelStoreMeansTheDefault() {
        JSynConfig cfg = JSynConfig.builder().modelStore("/x").modelStore(null).build();
        assertNull(cfg.modelStore);
    }

    @Test
    void defaultsAreReasonable() {
        JSynConfig cfg = JSynConfig.builder().build();
        assertEquals(10000, cfg.rtpPortMin);
        assertEquals(20000, cfg.rtpPortMax);
        assertEquals(200, cfg.rtpJitterBufferMs);
        assertNotNull(cfg.webrtcStunServer);
    }

    @Test
    void toJsonCarriesModelStoreUnderTheNameTheNativeSideReads() {
        String json = JSynConfig.builder().modelStore("/opt/models").build().toJson();
        assertTrue(json.contains("\"modelStore\":\"/opt/models\""), json);
        String dflt = JSynConfig.builder().build().toJson();
        assertFalse(dflt.contains("modelStore"), "an unset store is omitted: " + dflt);
    }

    @Test
    void licensingDefaultsLeaveTheNativeSideToResolve() {
        JSynConfig cfg = JSynConfig.builder().build();
        assertNull(cfg.licenseKey, "null reads $SYNAUSON_LICENSE_KEY natively");
        assertNull(cfg.licenseFile);
        assertNull(cfg.stateDir);
        assertFalse(cfg.offline);
        String json = cfg.toJson();
        assertFalse(json.contains("licenseKey"), json);
        assertTrue(json.contains("\"offline\":false"), json);
    }

    @Test
    void toJsonCarriesLicensingUnderTheNamesTheNativeSideReads() {
        String json = JSynConfig.builder()
            .licenseKey("KEY-1")
            .licenseFile("/etc/synauson/license.lic")
            .stateDir("/var/lib/app/synauson")
            .offline(true)
            .build()
            .toJson();
        assertTrue(json.contains("\"licenseKey\":\"KEY-1\""), json);
        assertTrue(json.contains("\"licenseFile\":\"/etc/synauson/license.lic\""), json);
        assertTrue(json.contains("\"stateDir\":\"/var/lib/app/synauson\""), json);
        assertTrue(json.contains("\"offline\":true"), json);
    }
}
