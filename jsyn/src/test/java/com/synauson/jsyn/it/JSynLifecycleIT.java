package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import com.synauson.jsyn.participant.Conference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JSynLifecycleIT {

    static JSynConfig cfg() {
        return JSynConfig.builder()
            .modelsDir(java.nio.file.Path.of("../../../models").toAbsolutePath().toString())
            .build();
    }

    @Test
    void startTerminateClose() throws Exception {
        try (JSyn syn = new JSyn(cfg())) {
            try (Conference conf = syn.startConference("lifecycle-test-1")) {
                assertEquals("lifecycle-test-1", conf.id());
                assertNotNull(conf.state());
            }
        }
    }

    @Test
    void multipleConferencesIsolated() throws Exception {
        try (JSyn syn = new JSyn(cfg())) {
            try (Conference a = syn.startConference("iso-a");
                 Conference b = syn.startConference("iso-b")) {
                assertEquals("iso-a", a.id());
                assertEquals("iso-b", b.id());
            }
        }
    }
}
