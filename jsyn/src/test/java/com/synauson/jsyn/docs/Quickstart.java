package com.synauson.jsyn.docs;

// The README's quickstart is the region between the snippet markers below, and
// :jsyn:checkReadmeSnippets fails when the two differ. compileTestJava compiles
// this file, so the README cannot show code that does not build. Nothing runs it.

// snippet: quickstart
import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.FileEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.spec.FileParticipantSpec;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Quickstart {
    public static void main(String[] args) throws Exception {
        String uri = Path.of(args[0]).toUri().toString(); // a WAV file -> file:///...

        JSynConfig config = JSynConfig.builder()
            .licenseKey(System.getenv("SYNAUSON_LICENSE_KEY"))
            .build();

        try (JSyn jsyn = new JSyn(config);
             Conference conf = jsyn.startConference("quickstart")) {
            CountDownLatch done = new CountDownLatch(1);

            // Subscribe before adding the participant so no event is missed.
            // Events arrive on a native thread: hand work off, don't block it.
            try (Subscription events = conf.streamFileEvents("player", event -> {
                System.out.println(event.getClass().getSimpleName());
                if (event instanceof FileEvent.Eos || event instanceof FileEvent.FileError) {
                    done.countDown();
                }
            })) {
                conf.addFileParticipant(FileParticipantSpec.builder()
                    .id("player")
                    .uri(uri)
                    .build());
                done.await(60, TimeUnit.SECONDS);
            }
        } // closing the conference, then the runtime, frees every native resource
    }
}
// end snippet: quickstart
