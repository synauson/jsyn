package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.JSynConfig;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.WebRtcEffectiveOptions;
import com.synauson.jsyn.WebRtcStats;
import com.synauson.jsyn.exception.InvalidArgumentException;
import com.synauson.jsyn.it.support.WebRtcBrowserPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.WebRtcParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.IceTransportPolicy;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Per-participant WebRTC options set through jsyn, checked against a real headless Chromium
 * call: the port the browser's selected ICE pair uses on the synauson side, the candidates
 * synauson offers, and the options {@link WebRtcParticipantHandle#stats()} reads back from
 * the native media elements.
 *
 * <p>Native runtimes older than 1.5.0 ignore the options and report no
 * {@link WebRtcStats#effectiveOptions}; against those the tests are skipped.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class WebRtcOptionsE2eIT {

    /** ICE ranges start here: clear of the SIP ranges and the OS ephemeral ports. */
    private static final AtomicInteger NEXT_ICE_PORT = new AtomicInteger(23_000);

    private static JSyn syn;
    private static boolean optionsSupported;

    /**
     * Start the runtime and check, with one real call, that it reports effective options.
     * The probe sets {@code stunServer} and {@code jitterBufferMs} because runtimes before
     * 1.5.0 require both.
     */
    @BeforeAll
    static void startRuntime() throws Exception {
        syn = JSynTestHelpers.newJSyn();
        Conference conf = syn.startConference("webrtc-options-probe-" + System.nanoTime());
        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer();
             Call call = Call.start(conf, WebRtcParticipantSpec.builder()
                     .participantId("probe")
                     .sdpOffer(browser.createOffer())
                     .stunServer("")
                     .jitterBufferMs(200)
                     .build(), browser)) {
            call.awaitConnected(Duration.ofSeconds(20));
            optionsSupported = call.handle.stats().effectiveOptions != null;
        } finally {
            conf.close();
        }
    }

    @AfterAll
    static void stopRuntime() {
        syn.close();
    }

    @Test
    void participantOptionsReachTheMediaPath() throws Exception {
        assumeOptionsSupported();
        int[] range = freePortBlock(20);
        Conference conf = syn.startConference("webrtc-options-" + System.nanoTime());
        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer()) {
            WebRtcParticipantSpec spec = WebRtcParticipantSpec.builder()
                    .participantId("caller")
                    .sdpOffer(browser.createOffer())
                    .stunServer("")
                    .jitterBufferMs(150)
                    .icePortRange(range[0], range[1])
                    .opusBitrate(24_000)
                    .opusDtx(false)
                    .opusInbandFec(true)
                    .build();
            try (Call call = Call.start(conf, spec, browser)) {
                // Echo the browser's fake microphone back to it: on GStreamer 1.24 a
                // participant with no audio routed to it is sent no RTP at all.
                conf.updatePartyAudioConnections(
                        new ConnectionMatrix(ConnectionEntry.connect("caller", "caller")));
                call.awaitConnected(Duration.ofSeconds(20));
                WebRtcEffectiveOptions eff = effectiveOptions(call.handle);

                assertMediaPortIn(browser, range);
                assertHostCandidatesIn(call.serverCandidates(), range);
                assertEquals(range[0], eff.icePortMin);
                assertEquals(range[1], eff.icePortMax);
                assertEquals(150, eff.jitterBufferMs);
                assertEquals(24_000, eff.opusBitrate);
                assertFalse(eff.opusDtx);
                assertTrue(eff.opusInbandFec);
                assertEquals(10, eff.opusPacketLossPercentage,
                        "FEC without a loss percentage gets the 10% default");
                assertEquals("", eff.stunServer);
                assertEquals(IceTransportPolicy.ALL, eff.iceTransportPolicy);
                long deadline = System.currentTimeMillis() + 10_000;
                while (browser.receivedPackets() == 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100);
                }
                assertTrue(browser.receivedPackets() > 0,
                        "no RTP reached the browser over the constrained port");
            }
        } finally {
            conf.close();
        }
    }

    @Test
    void runtimeDefaultsApplyWhenTheParticipantSetsNone() throws Exception {
        assumeOptionsSupported();
        int[] range = freePortBlock(20);
        int rtpMin = JSynTestHelpers.nextRtpPortMin();
        try (JSyn defaults = new JSyn(JSynConfig.builder()
                .modelStore(JSynTestHelpers.modelStore().toString())
                .rtpPortMin(rtpMin)
                .rtpPortMax(rtpMin + 199)
                .webrtcStunServer("")
                .webrtcJitterBufferMs(120)
                .webrtcIcePortRange(range[0], range[1])
                .build())) {
            Conference conf = defaults.startConference("webrtc-defaults-" + System.nanoTime());
            try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer()) {
                WebRtcParticipantSpec spec = WebRtcParticipantSpec.builder()
                        .participantId("caller")
                        .sdpOffer(browser.createOffer())
                        .build();
                try (Call call = Call.start(conf, spec, browser)) {
                    call.awaitConnected(Duration.ofSeconds(20));
                    WebRtcEffectiveOptions eff = effectiveOptions(call.handle);

                    assertMediaPortIn(browser, range);
                    assertEquals(range[0], eff.icePortMin);
                    assertEquals(range[1], eff.icePortMax);
                    assertEquals(120, eff.jitterBufferMs);
                    assertEquals(64_000, eff.opusBitrate);
                    assertTrue(eff.opusDtx);
                }
            } finally {
                conf.close();
            }
        }
    }

    @Test
    void invalidOptionsAreRejectedWithoutLeakingCredentials() throws Exception {
        assumeOptionsSupported();
        Conference conf = syn.startConference("webrtc-invalid-" + System.nanoTime());
        try (WebRtcBrowserPeer browser = new WebRtcBrowserPeer()) {
            String offer = browser.createOffer();
            InvalidArgumentException ports = assertThrows(InvalidArgumentException.class,
                    () -> conf.addWebRtcParticipant(WebRtcParticipantSpec.builder()
                            .participantId("bad-ports").sdpOffer(offer)
                            .icePortRange(30_010, 30_000).build()));
            assertTrue(ports.getMessage().contains("ice_port_min"), ports.getMessage());

            InvalidArgumentException relay = assertThrows(InvalidArgumentException.class,
                    () -> conf.addWebRtcParticipant(WebRtcParticipantSpec.builder()
                            .participantId("bad-relay").sdpOffer(offer)
                            .iceTransportPolicy(IceTransportPolicy.RELAY).build()));
            assertTrue(relay.getMessage().contains("ice_transport_policy"), relay.getMessage());

            InvalidArgumentException turn = assertThrows(InvalidArgumentException.class,
                    () -> conf.addWebRtcParticipant(WebRtcParticipantSpec.builder()
                            .participantId("bad-turn").sdpOffer(offer)
                            .turnServers(List.of("turn://1735689600:alice:s3cret@turn.example"))
                            .build()));
            assertTrue(turn.getMessage().contains("turn_servers"), turn.getMessage());
            assertFalse(turn.getMessage().contains("s3cret"),
                    "the error leaks the TURN password: " + turn.getMessage());
        } finally {
            conf.close();
        }
    }

    // -------------------------------------------------------------------------

    private static void assumeOptionsSupported() {
        assumeTrue(optionsSupported, "native runtime predates per-participant WebRTC options");
    }

    private static WebRtcEffectiveOptions effectiveOptions(WebRtcParticipantHandle handle) {
        WebRtcStats stats = handle.stats();
        assertNotNull(stats.effectiveOptions, "stats carry no effective options");
        return stats.effectiveOptions;
    }

    /** The browser's selected ICE pair must end, on the synauson side, at a UDP port in range. */
    private static void assertMediaPortIn(WebRtcBrowserPeer browser, int[] range)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        Map<String, Object> selected = browser.selectedRemoteCandidate();
        while (selected == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            selected = browser.selectedRemoteCandidate();
        }
        assertNotNull(selected, "the browser selected no ICE candidate pair");
        int port = ((Number) selected.get("port")).intValue();
        assertEquals("udp", selected.get("protocol"), selected.toString());
        assertTrue(port >= range[0] && port <= range[1],
                "media flows on synauson port " + port + " " + selected
                        + ", outside " + range[0] + "-" + range[1]);
    }

    /**
     * Every UDP and TCP-passive host candidate synauson offered must be inside the range.
     * TCP-active candidates advertise port 9 and have no bound port.
     */
    private static void assertHostCandidatesIn(List<String> candidates, int[] range) {
        int udpHosts = 0;
        for (String candidate : candidates) {
            String[] f = candidate.replaceFirst("^a=", "").split(" ");
            if (f.length < 8 || !"host".equals(f[7]) || candidate.contains("tcptype active")) {
                continue;
            }
            int port = Integer.parseInt(f[5]);
            assertTrue(port >= range[0] && port <= range[1],
                    "host candidate outside " + range[0] + "-" + range[1] + ": " + candidate);
            if ("udp".equalsIgnoreCase(f[2])) {
                udpHosts++;
            }
        }
        assertTrue(udpHosts > 0, "synauson offered no UDP host candidate: " + candidates);
    }

    /** {@code len} consecutive ports that are free for UDP and TCP right now. */
    private static int[] freePortBlock(int len) {
        while (true) {
            int start = NEXT_ICE_PORT.getAndAdd(len);
            if (start + len > 30_000) {
                throw new IllegalStateException("no free ICE port block below 30000");
            }
            boolean free = true;
            for (int p = start; p < start + len && free; p++) {
                free = isFree(p);
            }
            if (free) {
                return new int[] {start, start + len - 1};
            }
        }
    }

    private static boolean isFree(int port) {
        InetAddress any = new InetSocketAddress(0).getAddress();
        try (DatagramSocket udp = new DatagramSocket(port, any);
             ServerSocket tcp = new ServerSocket(port, 1, any)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** One negotiated browser call plus its trickle-ICE relay. */
    private static final class Call implements AutoCloseable {
        final WebRtcBrowserPeer browser;
        final WebRtcParticipantHandle handle;
        final Subscription iceSub;
        // jsyn delivers candidates on a native thread; Playwright is single-threaded,
        // so they are queued here and handed to the browser from the test thread.
        final ConcurrentLinkedQueue<Object[]> remoteCandidates = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> seen = new ConcurrentLinkedQueue<>();

        private Call(WebRtcBrowserPeer browser, WebRtcParticipantHandle handle,
                     Conference conf, String pid) {
            this.browser = browser;
            this.handle = handle;
            this.iceSub = conf.streamWebRtcIceCandidates(pid, ev -> {
                if (!ev.endOfCandidates) {
                    remoteCandidates.add(new Object[] {ev.candidate, ev.sdpMLineIndex});
                    seen.add(ev.candidate);
                }
            });
        }

        static Call start(Conference conf, WebRtcParticipantSpec spec, WebRtcBrowserPeer browser) {
            WebRtcParticipantHandle handle = conf.addWebRtcParticipant(spec);
            browser.applyAnswer(handle.sdpAnswer());
            return new Call(browser, handle, conf, spec.participantId);
        }

        List<String> serverCandidates() {
            return new ArrayList<>(seen);
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
}
