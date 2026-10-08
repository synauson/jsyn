package com.synauson.jsyn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilitiesTest {
    // The shape the native CapabilitiesReport serializes to (serde, camelCase).
    private static final String JSON = "{"
        + "\"license\":{\"state\":\"expiring\",\"description\":\"license 'acme' expires 2026-11-01\","
        + "\"licenseId\":\"f5544dce\",\"name\":\"acme\",\"source\":\"server\","
        + "\"fileExpiry\":\"2026-10-27T16:25:22.204+00:00\",\"licenseExpiry\":\"2026-11-01T12:00:00+00:00\","
        + "\"plan\":\"speech\",\"daysRemaining\":27,\"problem\":null},"
        + "\"limitsScope\":\"this instance\",\"overdraft\":0.25,"
        + "\"capabilities\":["
        + "{\"code\":\"FEATURE_VAD\",\"entitled\":true,\"includedBy\":\"FEATURE_STT\"},"
        + "{\"code\":\"FEATURE_TURN_DETECTION\",\"entitled\":true,\"includedBy\":\"FEATURE_STT\"},"
        + "{\"code\":\"FEATURE_STT\",\"entitled\":true,\"includedBy\":null},"
        + "{\"code\":\"FEATURE_TTS\",\"entitled\":true,\"includedBy\":null}],"
        + "\"models\":["
        + "{\"id\":\"sentito-1\",\"version\":\"5\",\"release\":\"5.0.0\",\"state\":\"ready\",\"detail\":null},"
        + "{\"id\":\"fermata-1\",\"version\":\"1.0.0-cpu\",\"release\":\"1.0.0\",\"state\":\"missing\",\"detail\":\"not installed\"},"
        + "{\"id\":\"spartito-1\",\"version\":\"1.0.0-int8\",\"release\":\"1.0.0\",\"state\":\"ready\",\"detail\":null},"
        + "{\"id\":\"lettura-1\",\"version\":\"1.0.0-fp32\",\"release\":\"1.0.0\",\"state\":\"downloading\",\"detail\":null}],"
        + "\"stt\":{\"state\":\"ready\",\"calibrated\":true,\"workers\":3,\"threadsPerWorker\":5,"
        + "\"realTimeFactor\":0.79,\"modelBytes\":64487424,\"sharedModelBytes\":603979776,\"limitedBy\":\"cpu\","
        + "\"streams\":{\"limit\":3,\"inUse\":1},\"detail\":null,"
        + "\"turnFlush\":true,\"forecastReserve\":0.384,\"source\":\"cached\"},"
        + "\"sessions\":{\"limit\":20,\"inUse\":17,\"ceiling\":25,\"level\":\"near-limit\","
        + "\"peak\":19,\"peakAt\":\"2026-10-04T09:12:00+00:00\"},"
        + "\"resources\":{\"cpuBudget\":1.5,\"cpuSource\":\"auto\",\"limitedBy\":\"cpu_max\","
        + "\"cores\":1,\"logicalCpus\":8,\"physicalCores\":4,\"performanceCpus\":null,"
        + "\"cpuModel\":\"Intel(R) Xeon(R) Platinum 8481C CPU @ 2.70GHz\","
        + "\"cpuFlags\":[\"avx2\",\"avx512f\",\"avx512vnni\",\"amx_tile\",\"amx_int8\"],"
        + "\"memoryLimitBytes\":3221225472,\"memorySource\":\"auto\"},"
        + "\"calibration\":{\"file\":\"/var/lib/app/synauson/calibration.json\",\"recalibrate\":false,"
        + "\"vad\":{\"model\":\"sentito-1\",\"threads\":1,\"ms\":0.21,\"source\":\"cached\"},"
        + "\"turnDetection\":null}"
        + "}";

    // What natives before the session pool sent.
    private static final String OLDER = "{"
        + "\"license\":{\"state\":\"licensed\",\"description\":\"license 'free'\","
        + "\"licenseId\":\"f5544dce\",\"name\":\"free\",\"source\":\"server\","
        + "\"fileExpiry\":\"2026-10-27T16:25:22.204+00:00\",\"licenseExpiry\":null},"
        + "\"limitsScope\":\"this instance\",\"overdraft\":0.25,"
        + "\"conferences\":{\"limit\":10,\"inUse\":3},"
        + "\"aiConferences\":{\"limit\":2,\"inUse\":1},"
        + "\"capabilities\":["
        + "{\"code\":\"FEATURE_VAD\",\"entitled\":true,\"streams\":{\"limit\":null,\"inUse\":0}}],"
        + "\"models\":[]"
        + "}";

    @Test
    void readsTheNativeReport() {
        Capabilities c = Capabilities.fromJson(JSON);
        assertEquals("expiring", c.license.state);
        assertEquals("acme", c.license.name);
        assertEquals("speech", c.license.plan);
        assertEquals(Long.valueOf(27), c.license.daysRemaining);
        assertNull(c.license.problem);
        assertEquals("this instance", c.limitsScope);
        assertEquals(0.25, c.overdraft);

        assertNotNull(c.sessions);
        assertEquals(Integer.valueOf(20), c.sessions.limit);
        assertEquals(17, c.sessions.inUse);
        assertEquals(Integer.valueOf(25), c.sessions.ceiling);
        assertEquals("near-limit", c.sessions.level);
        assertEquals(19, c.sessions.peak);
        assertEquals("2026-10-04T09:12:00+00:00", c.sessions.peakAt);

        assertEquals(4, c.capabilities.size());
        assertEquals("FEATURE_TURN_DETECTION", c.capabilities.get(1).code);
        assertEquals("FEATURE_STT", c.capabilities.get(0).includedBy);
        assertEquals("FEATURE_STT", c.capabilities.get(1).includedBy);
        assertEquals("FEATURE_TTS", c.capabilities.get(3).code);
        assertTrue(c.capabilities.get(3).entitled);
        assertNull(c.capabilities.get(3).includedBy);

        assertEquals("ready", c.models.get(0).state);
        assertEquals("missing", c.models.get(1).state);
        assertEquals("not installed", c.models.get(1).detail);
        assertEquals(4, c.models.size());
        assertEquals("lettura-1", c.models.get(3).id);
        assertEquals("1.0.0-fp32", c.models.get(3).version);
        assertEquals("downloading", c.models.get(3).state);

        assertNotNull(c.resources);
        assertEquals(1.5, c.resources.cpuBudget, 1e-9);
        assertEquals("cpu_max", c.resources.limitedBy);
        assertEquals(1, c.resources.cores);
        assertEquals(Integer.valueOf(4), c.resources.physicalCores);
        assertNull(c.resources.performanceCpus);
        assertTrue(c.resources.cpuFlags.contains("amx_int8"));
        assertEquals(Long.valueOf(3221225472L), c.resources.memoryLimitBytes);

        assertNotNull(c.stt);
        assertEquals("ready", c.stt.state);
        assertEquals(Boolean.TRUE, c.stt.calibrated);
        assertEquals(Long.valueOf(64487424L), c.stt.modelBytes);
        assertEquals(Long.valueOf(603979776L), c.stt.sharedModelBytes);
        assertEquals("cpu", c.stt.limitedBy);
        assertEquals(Integer.valueOf(3), c.stt.workers);
        assertEquals(Integer.valueOf(5), c.stt.threadsPerWorker);
        assertEquals(0.79, c.stt.realTimeFactor);
        assertEquals(Integer.valueOf(3), c.stt.streams.limit);
        assertEquals(1, c.stt.streams.inUse);
        assertNull(c.stt.detail);
        assertEquals(Boolean.TRUE, c.stt.turnFlush);
        assertEquals(Double.valueOf(0.384), c.stt.forecastReserve);
        assertEquals("cached", c.stt.source);

        assertNotNull(c.calibration);
        assertEquals("/var/lib/app/synauson/calibration.json", c.calibration.file);
        assertFalse(c.calibration.recalibrate);
        assertNotNull(c.calibration.vad);
        assertEquals("sentito-1", c.calibration.vad.model);
        assertEquals(1, c.calibration.vad.threads);
        assertEquals(0.21, c.calibration.vad.ms, 1e-9);
        assertEquals("cached", c.calibration.vad.source);
        assertNull(c.calibration.turnDetection, "not timed yet");
    }

    @Test
    void nativesOlderThanTheCalibrationCacheReportNoSource() {
        String older = JSON.replace(",\"source\":\"cached\"}", "}");
        older = older.substring(0, older.indexOf(",\"calibration\"")) + "}";
        Capabilities c = Capabilities.fromJson(older);
        assertNotNull(c.stt);
        assertNull(c.stt.source);
        assertNull(c.calibration);
        assertNotNull(c.resources);
    }

    @Test
    @SuppressWarnings("deprecation")
    void theSessionPoolReplacesTheOldLimits() {
        Capabilities c = Capabilities.fromJson(JSON);
        assertNull(c.conferences);
        assertNull(c.aiConferences);
        assertNull(c.capabilities.get(0).streams);
    }

    @Test
    @SuppressWarnings("deprecation")
    void nativesOlderThanTheSessionPoolReportTheOldLimits() {
        Capabilities c = Capabilities.fromJson(OLDER);
        assertEquals("licensed", c.license.state);
        assertNull(c.license.plan);
        assertNull(c.license.daysRemaining);
        assertNull(c.sessions);
        assertEquals(Integer.valueOf(10), c.conferences.limit);
        assertEquals(1, c.aiConferences.inUse);
        assertNull(c.capabilities.get(0).streams.limit, "null is unlimited");
    }

    @Test
    void nativesOlderThanTheTurnFlushReportNeither() {
        String older = JSON.replace(",\"turnFlush\":true,\"forecastReserve\":0.384", "");
        Capabilities c = Capabilities.fromJson(older);
        assertNotNull(c.stt);
        assertNull(c.stt.turnFlush);
        assertNull(c.stt.forecastReserve);
    }

    @Test
    void nativesOlderThanInclusionReportNoIncludedBy() {
        String older = JSON.replace(",\"includedBy\":\"FEATURE_STT\"", "")
            .replace(",\"includedBy\":null", "");
        Capabilities c = Capabilities.fromJson(older);
        assertTrue(c.capabilities.get(0).entitled);
        assertNull(c.capabilities.get(0).includedBy);
    }

    @Test
    void nativesOlderThanTtsReportThreeCapabilities() {
        String older = JSON.replace(",{\"code\":\"FEATURE_TTS\",\"entitled\":true,\"includedBy\":null}", "");
        Capabilities c = Capabilities.fromJson(older);
        assertEquals(3, c.capabilities.size());
        assertEquals("FEATURE_STT", c.capabilities.get(2).code);
    }

    @Test
    void nativesOlderThanSttReportNoCapacity() {
        String older = OLDER;
        assertNull(Capabilities.fromJson(older).stt);
    }
}
