package com.synauson.jsyn.it;

import com.synauson.jsyn.JSyn;
import com.synauson.jsyn.NativeAudioFormat;
import com.synauson.jsyn.it.support.NativeAudioPump;
import com.synauson.jsyn.it.support.RtpPacket;
import com.synauson.jsyn.it.support.SipRtpPeer;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.participant.NativeParticipant;
import com.synauson.jsyn.participant.SipParticipantHandle;
import com.synauson.jsyn.spec.ConnectionEntry;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.SipParticipantSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.synauson.jsyn.it.support.ToneAnalysis.awaitJointTone;
import static com.synauson.jsyn.it.support.ToneAnalysis.pcmuSamples;
import static com.synauson.jsyn.it.support.ToneAnalysis.strongestJointAmplitude;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SIP participants mixed with 16 kHz sources, over real UDP in real time.
 *
 * <p>SIP audio used to reach the conference at 8 kHz while native, WebRTC and
 * file participants carried 16 kHz. audiomixer converts sample format but not
 * rate, so a destination fed one of each refused whichever linked second
 * ({@code pad link failed: Noformat}). Every participant now normalizes to
 * S16LE/16 kHz/mono before its fanout tee, SIP transmit converts to the peer's
 * rate after the mix, G.711 leaves in 20 ms packets, and in-band DTMF
 * (dtmfPayloadType 0) is generated at the peer's rate after the mix.
 *
 * <p>Every assertion is on decoded audio: PCMU captured off the wire by a
 * {@link SipRtpPeer}, or PCM drained from a native participant's egress ring,
 * measured with a Goertzel filter at the frequencies each source sends.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class SipMixedSourcesE2eIT {

    private static final NativeAudioFormat FORMAT = NativeAudioFormat.PCM_S16LE16K_MONO;
    private static final double SIP_TONE_HZ = 440.0;
    private static final double NATIVE_TONE_HZ = 1000.0;
    /** A tone at peak 8000-12000 measures in the thousands; mu-law noise and leakage stay in the low hundreds. */
    private static final double PRESENT = 1000.0;
    /** 200 ms windows: 5 Hz bins, so neighbouring test tones do not leak into each other. */
    private static final int WINDOW_8K = 1600;
    private static final int WINDOW_16K = 3200;
    private static final Duration HEAR_WITHIN = Duration.ofSeconds(10);

    @Test
    void sipCalleeHearsNativeAndSipSourcesTogetherIn20msPackets() throws Exception {
        long ts = System.nanoTime();
        String caller = "mix-caller-" + ts;
        String callee = "mix-callee-" + ts;
        String bot = "mix-bot-" + ts;

        try (SipRtpPeer callerPeer = new SipRtpPeer();
             SipRtpPeer calleePeer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("mix-callee-conf-" + ts)) {

            SipParticipantHandle callerHandle = conf.addSipParticipant(sipSpec(caller, callerPeer, 101));
            callerPeer.setTarget("127.0.0.1", callerHandle.localRtpPort());
            callerPeer.startTone(SIP_TONE_HZ);
            SipParticipantHandle calleeHandle = conf.addSipParticipant(sipSpec(callee, calleePeer, 101));
            calleePeer.setTarget("127.0.0.1", calleeHandle.localRtpPort());

            try (NativeParticipant botNp = conf.addNativeParticipant(bot,
                         NativeParticipantSpec.builder().format(FORMAT).build());
                 NativeAudioPump botPump = NativeAudioPump.tone(botNp, NATIVE_TONE_HZ)) {

                // The 16 kHz source links first, so the callee's transmit mixer is
                // configured at 16 kHz before the SIP caller's audio arrives: the
                // order that used to refuse the second link with Noformat.
                conf.updatePartyAudioConnections(new ConnectionMatrix(
                        ConnectionEntry.connect(bot, callee)));
                Thread.sleep(300);
                conf.updatePartyAudioConnections(new ConnectionMatrix(
                        ConnectionEntry.connect(bot, callee),
                        ConnectionEntry.connect(caller, callee)));

                double both = awaitJointTone(() -> pcmuSamples(calleePeer.capturedPackets()),
                        8000, WINDOW_8K, PRESENT, HEAR_WITHIN, SIP_TONE_HZ, NATIVE_TONE_HZ);
                assertTrue(both >= PRESENT, "SIP callee never heard the native 1 kHz tone and the SIP"
                        + " caller's 440 Hz tone together (weakest of the two peaked at " + (int) both
                        + "); " + describe(calleePeer.capturedPackets()));

                // Steady state: one second of what the callee receives now.
                calleePeer.clearCaptured();
                Thread.sleep(1000);
                List<RtpPacket> steady = pcmu(calleePeer.capturedPackets());
                assertTrue(steady.size() >= 25, "only " + steady.size()
                        + " PCMU packets reached the callee in 1 s");
                assertEquals(Map.of(160, (long) steady.size()), payloadSizes(steady),
                        "every PCMU payload must be 160 bytes (20 ms at 8 kHz, RFC 3551 section 4.5)");
                double steadyBoth = strongestJointAmplitude(pcmuSamples(steady), 8000, WINDOW_8K,
                        SIP_TONE_HZ, NATIVE_TONE_HZ);
                assertTrue(steadyBoth >= PRESENT, "both tones must keep flowing to the callee;"
                        + " weakest peaked at " + (int) steadyBoth);
                assertEquals(null, botPump.failure(), "native ring I/O failed");
            }
        }
    }

    @Test
    void sipCallerReachesA16kNativeParticipantBesideAnother16kSource() throws Exception {
        long ts = System.nanoTime();
        String caller = "n16-caller-" + ts;
        String voice = "n16-voice-" + ts;
        String bot = "n16-bot-" + ts;

        try (SipRtpPeer callerPeer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("n16-conf-" + ts)) {

            SipParticipantHandle callerHandle = conf.addSipParticipant(sipSpec(caller, callerPeer, 101));
            callerPeer.setTarget("127.0.0.1", callerHandle.localRtpPort());
            callerPeer.startTone(SIP_TONE_HZ);

            try (NativeParticipant voiceNp = conf.addNativeParticipant(voice,
                         NativeParticipantSpec.builder().format(FORMAT).build());
                 NativeParticipant botNp = conf.addNativeParticipant(bot,
                         NativeParticipantSpec.builder().format(FORMAT).build());
                 NativeAudioPump voicePump = NativeAudioPump.tone(voiceNp, NATIVE_TONE_HZ);
                 NativeAudioPump botPump = NativeAudioPump.listen(botNp)) {

                // SIP caller alone first: its tone must come out of the bot's read().
                conf.updatePartyAudioConnections(new ConnectionMatrix(
                        ConnectionEntry.connect(caller, bot)));
                double heard = awaitJointTone(botPump::capturedSamples, FORMAT.sampleRate(), WINDOW_16K,
                        PRESENT, HEAR_WITHIN, SIP_TONE_HZ);
                assertTrue(heard >= PRESENT, "native participant never read the SIP caller's 440 Hz"
                        + " tone (peak " + (int) heard + ", " + botPump.capturedSamples().length
                        + " samples read)");

                // Then a 16 kHz native source joins the same destination mixer.
                conf.updatePartyAudioConnections(new ConnectionMatrix(
                        ConnectionEntry.connect(caller, bot),
                        ConnectionEntry.connect(voice, bot)));
                botPump.clearCaptured();
                double both = awaitJointTone(botPump::capturedSamples, FORMAT.sampleRate(), WINDOW_16K,
                        PRESENT, HEAR_WITHIN, SIP_TONE_HZ, NATIVE_TONE_HZ);
                assertTrue(both >= PRESENT, "native participant never read the SIP 440 Hz tone and the"
                        + " native 1 kHz tone together (weakest peaked at " + (int) both + ")");
                assertEquals(null, botPump.failure(), "native ring I/O failed");
                assertEquals(null, voicePump.failure(), "native ring I/O failed");
            }
        }
    }

    @Test
    void inBandDtmfIsMixedWithConferenceAudioAtThePeerRate() throws Exception {
        long ts = System.nanoTime();
        String caller = "ib-caller-" + ts;
        String bot = "ib-bot-" + ts;
        // ITU-T Q.23: digit 5 is 770 Hz + 1336 Hz.
        double low = 770.0;
        double high = 1336.0;
        // 50 ms windows: short enough to sit inside a 400 ms digit, 20 Hz bins
        // keep the 1 kHz conference tone out of the 770 Hz and 1336 Hz bins.
        int window = 400;

        try (SipRtpPeer callerPeer = new SipRtpPeer();
             JSyn syn = JSynTestHelpers.newJSyn();
             Conference conf = syn.startConference("ib-conf-" + ts)) {

            // dtmfPayloadType 0: no RFC 4733 telephone-event, so sendDtmf goes in-band.
            SipParticipantHandle callerHandle = conf.addSipParticipant(sipSpec(caller, callerPeer, 0));
            callerPeer.setTarget("127.0.0.1", callerHandle.localRtpPort());

            try (NativeParticipant botNp = conf.addNativeParticipant(bot,
                         NativeParticipantSpec.builder().format(FORMAT).build());
                 NativeAudioPump botPump = NativeAudioPump.tone(botNp, NATIVE_TONE_HZ)) {

                conf.updatePartyAudioConnections(new ConnectionMatrix(
                        ConnectionEntry.connect(bot, caller)));
                double voice = awaitJointTone(() -> pcmuSamples(callerPeer.capturedPackets()),
                        8000, WINDOW_8K, PRESENT, HEAR_WITHIN, NATIVE_TONE_HZ);
                assertTrue(voice >= PRESENT, "SIP caller never heard the native 1 kHz tone (peak "
                        + (int) voice + "); " + describe(callerPeer.capturedPackets()));

                // Before any digit: conference tone only, no DTMF pair.
                callerPeer.clearCaptured();
                Thread.sleep(1000);
                double idle = strongestJointAmplitude(pcmuSamples(callerPeer.capturedPackets()),
                        8000, window, low, high);
                assertTrue(idle < 400, "DTMF pair already present before sendDtmf (" + (int) idle + ")");

                callerPeer.clearCaptured();
                callerHandle.sendDtmf('5', 400);
                double digit = awaitJointTone(() -> pcmuSamples(callerPeer.capturedPackets()),
                        8000, window, PRESENT, Duration.ofSeconds(5), low, high);
                List<RtpPacket> captured = callerPeer.capturedPackets();
                assertTrue(digit >= PRESENT, "in-band digit 5 (770 Hz + 1336 Hz) never reached the"
                        + " caller (weakest peaked at " + (int) digit + "); " + describe(captured));
                assertTrue(captured.stream().allMatch(p -> p.payloadType == 0),
                        "in-band DTMF must ride the PCMU stream, not a telephone-event payload; "
                                + describe(captured));
                double voiceDuringDigit = strongestJointAmplitude(pcmuSamples(captured), 8000, WINDOW_8K,
                        NATIVE_TONE_HZ);
                assertTrue(voiceDuringDigit >= PRESENT, "conference audio stopped around the digit"
                        + " (1 kHz peaked at " + (int) voiceDuringDigit + ")");
                assertEquals(null, botPump.failure(), "native ring I/O failed");
            }
        }
    }

    // -------------------------------------------------------------------------

    static SipParticipantSpec sipSpec(String pid, SipRtpPeer peer, int dtmfPayloadType) {
        return SipParticipantSpec.builder()
                .participantId(pid)
                .remoteIp("127.0.0.1")
                .remoteRtpPort(peer.localPort())
                .codec("PCMU")
                .dtmfPayloadType(dtmfPayloadType)
                .build();
    }

    static List<RtpPacket> pcmu(List<RtpPacket> packets) {
        return packets.stream().filter(p -> p.payloadType == 0).collect(Collectors.toList());
    }

    /** Payload size (bytes) to packet count, sorted by size. */
    static Map<Integer, Long> payloadSizes(List<RtpPacket> packets) {
        return packets.stream().collect(Collectors.groupingBy(p -> p.payload.length, TreeMap::new,
                Collectors.counting()));
    }

    /** A one-line summary of a capture for assertion messages. */
    static String describe(List<RtpPacket> packets) {
        Map<Integer, Long> byType = packets.stream().collect(Collectors.groupingBy(p -> p.payloadType,
                TreeMap::new, Collectors.counting()));
        return packets.size() + " packets captured, by payload type " + byType
                + ", PCMU payload sizes " + payloadSizes(pcmu(packets));
    }
}
