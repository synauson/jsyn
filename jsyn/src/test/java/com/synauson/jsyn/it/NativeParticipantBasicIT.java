package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.NativeParticipantStats;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeParticipantBasicIT {
    @Test
    void addAndCloseNativeParticipant() throws Exception {
        try (JSyn syn = new JSyn(JSynLifecycleIT.cfg())) {
            try (Conference conf = syn.startConference("native-basic-it-1")) {
                // Conference.addNativeParticipant takes (String participantId, NativeParticipantSpec)
                try (NativeParticipant np = conf.addNativeParticipant("p1",
                    NativeParticipantSpec.builder()
                        .format(NativeAudioFormat.PCM_S16LE16K_MONO)
                        .build())) {

                    assertNotNull(np);
                    assertEquals(NativeAudioFormat.PCM_S16LE16K_MONO, np.format());

                    // Write a 20ms frame at 16k PCM: 16000 samples/sec * 2 bytes * 0.02s = 640 bytes
                    byte[] buf = new byte[640];
                    int written = np.write(buf, 0, buf.length);
                    assertTrue(written >= 0, "write() should return non-negative bytes");

                    NativeParticipantStats stats = np.stats();
                    assertNotNull(stats);
                }
            }
        }
    }
}
