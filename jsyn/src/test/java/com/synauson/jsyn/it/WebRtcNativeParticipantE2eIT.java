package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.WebRtcStats;
import com.synauson.jsyn.it.support.WebRtcBrowserPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.participant.WebRtcParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Voice-agent topology over a real browser: one conference per call, a
 * WebRTC participant for the headless Chromium caller and a
 * {@link NativeParticipant} for the agent, routed both ways. The agent writes
 * a real-time 440 Hz tone into its ingress ring (the text-to-speech path) and
 * drains its egress ring (the speech-to-text path).
 *
 * <p>Regression cover for two bugs a consumer hit replacing Kurento with
 * jsyn (github.com/n8thatcher/jsyn-mre):
 * <ul>
 *   <li>Outbound WebRTC audio intermittently never started: the transmit
 *       mixer could emit its first buffer before the payloader was linked to
 *       webrtcbin, the push returned {@code not-linked}, and the aggregator
 *       paused its task for the rest of the call. The browser then saw zero
 *       RTP while browser-to-jsyn audio kept working.</li>
 *   <li>{@code Conference.close()} intermittently never returned when the
 *       native participant was closed first, because conference teardown
 *       dropped the last native participant reference (nulling its
 *       audiomixer) while router connections still fed it.</li>
 * </ul>
 * Both were races, so each test repeats.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class WebRtcNativeParticipantE2eIT {

    private static final String STUN = "stun://stun.l.google.com:19302";
    private static final NativeAudioFormat FORMAT = NativeAudioFormat.PCM_S16LE8K_MONO;
    private static final String AGENT = "agent";
    private static final String CALLER = "caller";

    private static JSyn syn;

    @BeforeAll
    static void startRuntime() {
        syn = JSynTestHelpers.newJSyn();
    }

    @AfterAll
    static void stopRuntime() throws InterruptedException {
        // A hung close() in the teardown test must not also hang the suite.
        closeWithin(syn::close, Duration.ofSeconds(10));
    }

    @RepeatedTest(value = 8, name = "call {currentRepetition}")
    void nativeParticipantAudioReachesTheBrowser() throws Exception {
        String confId = "webrtc-native-audio-" + System.nanoTime();
        AtomicBoolean pumping = new AtomicBoolean(true);
        AtomicLong nonSilentFromBrowser = new AtomicLong();
        Thread pump = null;
        Conference conf = syn.startConference(confId);
        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer()) {
            // Production order: agent first, caller when the browser's offer arrives,
            // matrix wired before ICE connects.
            NativeParticipant agent = conf.addNativeParticipant(AGENT,
                    NativeParticipantSpec.builder().format(FORMAT).build());
            try (Call call = Call.start(conf, CALLER, browser)) {
                conf.updatePartyAudioConnections(new ConnectionMatrix(
                        ConnectionEntry.connect(CALLER, AGENT),
                        ConnectionEntry.connect(AGENT, CALLER)));
                call.awaitConnected(Duration.ofSeconds(20));
                pump = startTonePump(agent, pumping, nonSilentFromBrowser);

                boolean heard = browser.receivedNonSilentAudio(Duration.ofSeconds(15));
                WebRtcStats stats = call.handle.stats();
                assertTrue(heard, "browser never heard the native participant's tone:"
                        + " browser packetsReceived=" + browser.receivedPackets()
                        + ", synauson packetsSent=" + stats.packetsSent
                        + ", agent ring bytesWritten=" + agent.stats().bytesWritten);
                // Chromium's fake mic beeps about once a second, so give the
                // browser-to-agent direction a moment of its own.
                long inboundDeadline = System.currentTimeMillis() + 10_000;
                while (nonSilentFromBrowser.get() == 0 && System.currentTimeMillis() < inboundDeadline) {
                    Thread.sleep(100);
                }
                assertTrue(nonSilentFromBrowser.get() > 0,
                        "the agent never received the browser's fake-mic audio");
            }
        } finally {
            pumping.set(false);
            if (pump != null) {
                pump.join(1000);
            }
            assertTrue(closeWithin(conf::close, Duration.ofSeconds(10)),
                    "Conference.close() did not return");
        }
    }

    /**
     * The consumer's hang-up order: the signalling socket drops, the ICE
     * subscription is cancelled, the native participant is closed while its
     * I/O thread is still running, and only then is the conference closed.
     */
    @RepeatedTest(value = 5, name = "hang-up {currentRepetition}")
    void conferenceCloseReturnsWhenNativeParticipantIsClosedFirst() throws Exception {
        String confId = "webrtc-native-close-" + System.nanoTime();
        AtomicBoolean pumping = new AtomicBoolean(true);
        Thread pump;
        Conference conf = syn.startConference(confId);
        NativeParticipant agent = conf.addNativeParticipant(AGENT,
                NativeParticipantSpec.builder().format(FORMAT).build());
        boolean closed;
        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer()) {
            Call call = Call.start(conf, CALLER, browser);
            conf.updatePartyAudioConnections(new ConnectionMatrix(
                    ConnectionEntry.connect(CALLER, AGENT),
                    ConnectionEntry.connect(AGENT, CALLER)));
            call.awaitConnected(Duration.ofSeconds(20));
            pump = startTonePump(agent, pumping, new AtomicLong());
            Thread.sleep(3_000); // two-way media in flight

            call.close();
            agent.close();
            closed = closeWithin(conf::close, Duration.ofSeconds(10));
        }
        pumping.set(false);
        pump.join(1000);
        assertTrue(closed, "Conference.close() did not return within 10s after the"
                + " native participant was closed mid-call");
    }

    // -------------------------------------------------------------------------

    /** One negotiated browser call plus its trickle-ICE relay. */
    private static final class Call implements AutoCloseable {
        final WebRtcBrowserPeer browser;
        final WebRtcParticipantHandle handle;
        final Subscription iceSub;
        // jsyn delivers candidates on a native thread; Playwright is single-threaded,
        // so they are queued here and handed to the browser from the test thread.
        final ConcurrentLinkedQueue<Object[]> remoteCandidates;

        private Call(WebRtcBrowserPeer browser, WebRtcParticipantHandle handle,
                     Subscription iceSub, ConcurrentLinkedQueue<Object[]> remoteCandidates) {
            this.browser = browser;
            this.handle = handle;
            this.iceSub = iceSub;
            this.remoteCandidates = remoteCandidates;
        }

        static Call start(Conference conf, String pid, WebRtcBrowserPeer browser) {
            WebRtcParticipantHandle handle = conf.addWebRtcParticipant(
                    WebRtcParticipantSpec.builder()
                            .participantId(pid)
                            .sdpOffer(browser.createOffer())
                            .stunServer(STUN)
                            .jitterBufferMs(200)
                            .build());
            browser.applyAnswer(handle.sdpAnswer());
            ConcurrentLinkedQueue<Object[]> remote = new ConcurrentLinkedQueue<>();
            Subscription sub = conf.streamWebRtcIceCandidates(pid, ev -> {
                if (!ev.endOfCandidates) {
                    remote.add(new Object[] {ev.candidate, ev.sdpMLineIndex});
                }
            });
            return new Call(browser, handle, sub, remote);
        }

        void awaitConnected(Duration within) throws InterruptedException {
            long deadline = System.currentTimeMillis() + within.toMillis();
            String state = browser.connectionState();
            while (!"connected".equals(state) && System.currentTimeMillis() < deadline) {
                Object[] c;
                while ((c = remoteCandidates.poll()) != null) {
                    browser.addRemoteIceCandidate((String) c[0], (Integer) c[1]);
                }
                for (Map<String, Object> local : browser.drainLocalIceCandidates()) {
                    handle.addIceCandidate((String) local.get("candidate"),
                            ((Number) local.get("sdpMLineIndex")).intValue());
                }
                Thread.sleep(100);
                state = browser.connectionState();
            }
            assertEquals("connected", state, "WebRTC never connected");
        }

        @Override
        public void close() {
            iceSub.close();
        }
    }

    /**
     * Writes a real-time-paced 440 Hz tone into the agent's ingress ring and
     * drains its egress ring on one thread, counting non-silent samples the
     * browser's fake microphone delivered.
     */
    private static Thread startTonePump(NativeParticipant agent, AtomicBoolean running,
                                        AtomicLong nonSilentFromBrowser) {
        Thread t = new Thread(() -> {
            int rate = FORMAT.sampleRate();
            byte[] frame = new byte[FORMAT.bytesPer20ms()];
            byte[] drain = new byte[FORMAT.bytesPer20ms() * 4];
            long sample = 0;
            long start = System.nanoTime();
            try {
                for (long f = 0; running.get() && !agent.isClosed(); f++) {
                    for (int i = 0; i < frame.length / 2; i++, sample++) {
                        short s = (short) (16_000.0 * Math.sin(2.0 * Math.PI * 440.0 * sample / rate));
                        frame[i * 2] = (byte) (s & 0xff);
                        frame[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
                    }
                    agent.write(frame, 0, frame.length);
                    int n = agent.read(drain, 0, drain.length);
                    for (int i = 0; i + 1 < n; i += 2) {
                        short s = (short) ((drain[i] & 0xff) | (drain[i + 1] << 8));
                        if (Math.abs(s) > 1000) {
                            nonSilentFromBrowser.incrementAndGet();
                        }
                    }
                    long sleepNanos = start + (f + 1) * 20_000_000L - System.nanoTime();
                    if (sleepNanos > 0) {
                        Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // NativeResourceClosedException once the agent is closed under the pump.
            }
        }, "agent-tone-pump");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Runs {@code close} on a daemon thread; false if it is still running after {@code within}. */
    private static boolean closeWithin(Runnable close, Duration within) throws InterruptedException {
        Thread t = new Thread(close, "bounded-close");
        t.setDaemon(true);
        t.start();
        t.join(within.toMillis());
        return !t.isAlive();
    }
}
