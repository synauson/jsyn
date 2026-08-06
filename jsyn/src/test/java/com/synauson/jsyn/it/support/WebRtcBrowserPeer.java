package com.synauson.jsyn.it.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A real headless Chromium browser acting as the WebRTC counterparty for
 * {@code WebRtcMediaE2eIT}: real ICE, real DTLS-SRTP, real Opus.
 *
 * <p>Serves a tiny static page over {@code http://127.0.0.1} — a secure
 * context per the W3C spec's loopback exception, so {@code getUserMedia}
 * works without HTTPS — and drives it via Playwright's {@link Page#evaluate}.
 * Chromium is launched with {@code --use-fake-device-for-media-stream} and
 * {@code --use-fake-ui-for-media-stream} so no real camera/microphone
 * hardware or permission UI is needed; this is the standard, widely-used
 * approach for headless-CI WebRTC testing.
 */
public final class WebRtcBrowserPeer implements AutoCloseable {

    private static final String PAGE_HTML = String.join("\n",
            "<!DOCTYPE html>",
            "<html><body>",
            "<script>",
            "window.__pc = null;",
            "window.__iceQueue = [];",
            "window.__maxAmplitude = 0;",
            "",
            "async function createOffer() {",
            "  window.__pc = new RTCPeerConnection();",
            "  window.__pc.onicecandidate = function(event) {",
            "    if (event.candidate) {",
            "      window.__iceQueue.push({",
            "        candidate: event.candidate.candidate,",
            "        sdpMLineIndex: event.candidate.sdpMLineIndex",
            "      });",
            "    }",
            "  };",
            "  window.__pc.ontrack = function(event) {",
            "    var audioCtx = new AudioContext();",
            "    var source = audioCtx.createMediaStreamSource(event.streams[0]);",
            "    var analyser = audioCtx.createAnalyser();",
            "    analyser.fftSize = 2048;",
            "    source.connect(analyser);",
            "    var data = new Uint8Array(analyser.fftSize);",
            "    setInterval(function() {",
            "      analyser.getByteTimeDomainData(data);",
            "      var maxDev = 0;",
            "      for (var i = 0; i < data.length; i++) {",
            "        var dev = Math.abs(data[i] - 128);",
            "        if (dev > maxDev) maxDev = dev;",
            "      }",
            "      if (maxDev > window.__maxAmplitude) window.__maxAmplitude = maxDev;",
            "    }, 20);",
            "  };",
            "  var stream = await navigator.mediaDevices.getUserMedia({audio: true});",
            "  stream.getTracks().forEach(function(t) { window.__pc.addTrack(t, stream); });",
            "  var offer = await window.__pc.createOffer();",
            "  await window.__pc.setLocalDescription(offer);",
            "  return offer.sdp;",
            "}",
            "",
            "async function applyAnswer(sdp) {",
            "  await window.__pc.setRemoteDescription({type: 'answer', sdp: sdp});",
            "}",
            "",
            "async function addRemoteIceCandidate(candidate, sdpMLineIndex) {",
            "  await window.__pc.addIceCandidate({candidate: candidate, sdpMLineIndex: sdpMLineIndex});",
            "}",
            "",
            "function drainIceCandidates() {",
            "  var q = window.__iceQueue;",
            "  window.__iceQueue = [];",
            "  return q;",
            "}",
            "",
            "function maxObservedAmplitude() {",
            "  return window.__maxAmplitude;",
            "}",
            "</script>",
            "</body></html>"
    );

    private final HttpServer httpServer;
    private final Playwright playwright;
    private final Browser browser;
    private final Page page;

    /** Launch with Chromium's default synthetic fake-microphone tone. */
    public WebRtcBrowserPeer() throws IOException {
        this(null);
    }

    /**
     * Launch with a real audio file (e.g. a speech WAV) as the fake
     * microphone's input, via Chromium's {@code --use-file-for-fake-audio-capture}.
     *
     * @param fakeAudioCaptureFile path to a WAV file, or {@code null} for the default tone
     */
    public WebRtcBrowserPeer(String fakeAudioCaptureFile) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = PAGE_HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        Playwright pw = null;
        Browser br = null;
        try {
            List<String> args = new ArrayList<>(List.of(
                    "--use-fake-device-for-media-stream",
                    "--use-fake-ui-for-media-stream"));
            if (fakeAudioCaptureFile != null) {
                args.add("--use-file-for-fake-audio-capture=" + fakeAudioCaptureFile);
            }

            pw = Playwright.create();
            br = pw.chromium().launch(new BrowserType.LaunchOptions().setArgs(args));
            Page pg = br.newPage();
            pg.navigate("http://127.0.0.1:" + server.getAddress().getPort() + "/");

            this.httpServer = server;
            this.playwright = pw;
            this.browser = br;
            this.page = pg;
        } catch (RuntimeException e) {
            if (br != null) {
                br.close();
            }
            if (pw != null) {
                pw.close();
            }
            server.stop(0);
            throw e;
        }
    }

    /** Drive the browser to create a real SDP offer with a real ICE ufrag/DTLS fingerprint. */
    public String createOffer() {
        return (String) page.evaluate("() => createOffer()");
    }

    /** Apply synauson's real SDP answer to the browser's peer connection. */
    public void applyAnswer(String sdpAnswer) {
        page.evaluate("(sdp) => applyAnswer(sdp)", sdpAnswer);
    }

    /** Push a trickle-ICE candidate from synauson into the browser's peer connection. */
    public void addRemoteIceCandidate(String candidate, int sdpMLineIndex) {
        page.evaluate("(args) => addRemoteIceCandidate(args.candidate, args.sdpMLineIndex)",
                Map.of("candidate", candidate, "sdpMLineIndex", sdpMLineIndex));
    }

    /** Every local ICE candidate the browser has generated since the last drain. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> drainLocalIceCandidates() {
        Object result = page.evaluate("() => drainIceCandidates()");
        return result == null ? new ArrayList<>() : (List<Map<String, Object>>) result;
    }

    /**
     * Polls the browser's incoming-audio amplitude meter until it exceeds a
     * non-silence threshold or {@code within} elapses.
     */
    public boolean receivedNonSilentAudio(Duration within) {
        long deadline = System.currentTimeMillis() + within.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Object result = page.evaluate("() => maxObservedAmplitude()");
            double amplitude = ((Number) result).doubleValue();
            if (amplitude > 10.0) { // out of a possible 0-128 deviation range
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        try {
            page.close();
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            browser.close();
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            playwright.close();
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        httpServer.stop(0); // does not throw
        if (failure != null) {
            throw failure;
        }
    }
}
