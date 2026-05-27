package com.synauson.jsyn.participant;

import com.synauson.jsyn.ConferenceState;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.ResourceSnapshot;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.DtmfEvent;
import com.synauson.jsyn.event.FileEvent;
import com.synauson.jsyn.event.IceCandidateEvent;
import com.synauson.jsyn.event.SmartTurnEvent;
import com.synauson.jsyn.event.VadEvent;
import com.synauson.jsyn.internal.NativeBridge;
import com.synauson.jsyn.internal.NativeParticipantNativeHandle;
import com.synauson.jsyn.internal.NativeResource;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.FileParticipantSpec;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.PriorityFile;
import com.synauson.jsyn.spec.RecordingParticipantSpec;
import com.synauson.jsyn.spec.SipParticipantSpec;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Conference-scoped API handle.
 *
 * <p>Extends {@link NativeResource}: calling {@link #close()} terminates the conference
 * (equivalent to {@link #terminate()}) and releases all native resources associated with it.
 *
 * <p>All methods are thread-safe. Each delegates directly to a native bridge call with
 * a conference-scoped runtime handle.
 *
 * <p>Usage:
 * <pre>{@code
 * try (Conference conf = jsyn.startConference("conf-1")) {
 *     FileParticipantHandle alice = conf.addFileParticipant(
 *         FileParticipantSpec.builder().id("alice").uri("file:///audio.wav").build());
 *     // ...
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class Conference extends NativeResource {
    private static final Gson GSON = new Gson();

    private final long runtimeHandle;
    private final String conferenceId;

    /**
     * Construct a Conference handle. Called by the {@code JSyn} class after
     * {@code NativeBridge.startConference} returns.
     *
     * @param runtimeHandle opaque runtime handle from {@code NativeBridge.initRuntime}
     * @param conferenceId  conference identifier; non-null
     * @throws NullPointerException if {@code conferenceId} is null
     */
    public Conference(long runtimeHandle, String conferenceId) {
        super(() -> NativeBridge.terminateConference(runtimeHandle, conferenceId));
        this.runtimeHandle = runtimeHandle;
        this.conferenceId = Objects.requireNonNull(conferenceId, "conferenceId");
    }

    /**
     * Returns the conference identifier.
     *
     * @return the conference ID
     */
    public String id() { return conferenceId; }

    /**
     * Retrieve the current state of this conference (participant list, creation time).
     *
     * @return the conference state snapshot
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if closed
     * @throws com.synauson.jsyn.exception.NotFoundException if the conference has been
     *         terminated server-side
     */
    public ConferenceState state() {
        requireOpen();
        String json = NativeBridge.getConferenceState(runtimeHandle, conferenceId);
        return ConferenceState.fromJson(json);
    }

    /**
     * Terminate this conference, releasing all participants and native resources.
     * Equivalent to {@link #close()}.
     *
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if already closed
     */
    public void terminate() {
        close();
    }

    // -------------------------------------------------------------------------
    // Participant lifecycle
    // -------------------------------------------------------------------------

    /**
     * Add a file participant to this conference.
     *
     * @param spec the participant configuration
     * @return a handle to the newly added participant
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public FileParticipantHandle addFileParticipant(FileParticipantSpec spec) {
        requireOpen();
        Objects.requireNonNull(spec, "spec");
        String specJson = GSON.toJson(spec);
        String pid = NativeBridge.addFileParticipant(runtimeHandle, conferenceId, specJson);
        return new FileParticipantHandle(pid);
    }

    /**
     * Add a recording participant to this conference.
     *
     * @param spec the participant configuration
     * @return a handle to the newly added participant
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public RecordingParticipantHandle addRecordingParticipant(RecordingParticipantSpec spec) {
        requireOpen();
        Objects.requireNonNull(spec, "spec");
        String specJson = GSON.toJson(spec);
        String pid = NativeBridge.addRecordingParticipant(runtimeHandle, conferenceId, specJson);
        return new RecordingParticipantHandle(pid);
    }

    /**
     * Add a SIP participant to this conference.
     *
     * <p>The call blocks until the GStreamer pipeline is ready to receive RTP.
     * The returned handle includes the locally allocated RTP port.
     *
     * @param spec the participant configuration
     * @return a handle containing the participant ID and local RTP port
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public SipParticipantHandle addSipParticipant(SipParticipantSpec spec) {
        requireOpen();
        Objects.requireNonNull(spec, "spec");
        String specJson = GSON.toJson(spec);
        String resultJson = NativeBridge.addSipParticipant(runtimeHandle, conferenceId, specJson);
        JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
        String pid = result.get("participantId").getAsString();
        int localRtpPort = result.get("localRtpPort").getAsInt();
        return new SipParticipantHandle(runtimeHandle, conferenceId, pid, localRtpPort);
    }

    /**
     * Add a WebRTC participant to this conference.
     *
     * <p>The call blocks until the GStreamer webrtcbin processes the SDP offer and
     * generates an SDP answer. The caller must relay the SDP answer back to the browser
     * and exchange trickle-ICE candidates via {@link WebRtcParticipantHandle#addIceCandidate}
     * and {@link #streamWebRtcIceCandidates}.
     *
     * @param spec the participant configuration (must include the browser's SDP offer)
     * @return a handle containing the participant ID and the generated SDP answer
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public WebRtcParticipantHandle addWebRtcParticipant(WebRtcParticipantSpec spec) {
        requireOpen();
        Objects.requireNonNull(spec, "spec");
        String specJson = GSON.toJson(spec);
        String resultJson = NativeBridge.addWebRtcParticipant(runtimeHandle, conferenceId, specJson);
        JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
        String pid = result.get("participantId").getAsString();
        String sdpAnswer = result.get("sdpAnswer").getAsString();
        return new WebRtcParticipantHandle(runtimeHandle, conferenceId, pid, sdpAnswer);
    }

    /**
     * Add a native (in-process) participant to this conference.
     *
     * @param participantId the participant ID to assign
     * @param spec          the participant configuration
     * @return a handle with direct ring-buffer I/O
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public NativeParticipant addNativeParticipant(String participantId, NativeParticipantSpec spec) {
        requireOpen();
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(spec, "spec");
        String specJson = GSON.toJson(spec);
        NativeParticipantNativeHandle nativeHandle =
            NativeBridge.addNativeParticipant(runtimeHandle, conferenceId, participantId, specJson);
        return new NativeParticipant(conferenceId, participantId, nativeHandle);
    }

    /**
     * Remove a participant from this conference.
     *
     * @param participantId the ID of the participant to remove
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public void removeParticipant(String participantId) {
        requireOpen();
        NativeBridge.removeParticipant(runtimeHandle, conferenceId, participantId);
    }

    // -------------------------------------------------------------------------
    // Routing and control
    // -------------------------------------------------------------------------

    /**
     * Mute or unmute a participant's audio in the conference mix.
     *
     * @param participantId the participant to mute/unmute
     * @param muted         {@code true} to mute, {@code false} to unmute
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public void muteParticipant(String participantId, boolean muted) {
        requireOpen();
        NativeBridge.muteParticipant(runtimeHandle, conferenceId, participantId, muted);
    }

    /**
     * Replace the audio routing matrix for this conference.
     *
     * <p>All existing connections not present in {@code matrix} are removed.
     * Connections in {@code matrix} not currently present are added.
     *
     * @param matrix the desired audio connection topology
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public void updatePartyAudioConnections(ConnectionMatrix matrix) {
        requireOpen();
        Objects.requireNonNull(matrix, "matrix");
        String matrixJson = GSON.toJson(matrix);
        NativeBridge.updatePartyAudioConnections(runtimeHandle, conferenceId, matrixJson);
    }

    /**
     * Inject priority audio files into a participant's playback stream.
     *
     * <p>The files are played in order. Once all files finish, the participant's
     * normal audio source resumes.
     *
     * @param participantId the file participant to inject audio into
     * @param files         ordered list of audio files to play
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    public void addPriorityAudioFiles(String participantId, List<PriorityFile> files) {
        requireOpen();
        Objects.requireNonNull(files, "files");
        // Serialize as {"uris": ["...", ...]} matching PriorityAudioFilesJson in Rust.
        List<String> uris = files.stream().map(f -> f.uri).collect(Collectors.toList());
        JsonObject obj = new JsonObject();
        obj.add("uris", GSON.toJsonTree(uris));
        NativeBridge.addPriorityAudioFiles(runtimeHandle, conferenceId, participantId,
                                            GSON.toJson(obj));
    }

    // -------------------------------------------------------------------------
    // Event stream subscriptions
    // -------------------------------------------------------------------------

    /**
     * Subscribe to VAD events for a participant.
     *
     * @param participantId the participant to subscribe for
     * @param observer      receives {@link VadEvent.SpeechStart} and {@link VadEvent.SpeechEnd}
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    @SuppressWarnings("unchecked")
    public Subscription streamVadEvents(String participantId,
                                         EventStreamObserver<VadEvent> observer) {
        requireOpen();
        long subId = NativeBridge.subscribeVadEvents(runtimeHandle, conferenceId,
                                                      participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }

    /**
     * Subscribe to Smart Turn events for a participant.
     *
     * @param participantId the participant to subscribe for
     * @param observer      receives {@link SmartTurnEvent.TurnResult}
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    @SuppressWarnings("unchecked")
    public Subscription streamSmartTurnEvents(String participantId,
                                               EventStreamObserver<SmartTurnEvent> observer) {
        requireOpen();
        long subId = NativeBridge.subscribeSmartTurnEvents(runtimeHandle, conferenceId,
                                                            participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }

    /**
     * Subscribe to file playback events for a participant.
     *
     * @param participantId the participant to subscribe for
     * @param observer      receives {@link FileEvent} subtypes
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    @SuppressWarnings("unchecked")
    public Subscription streamFileEvents(String participantId,
                                          EventStreamObserver<FileEvent> observer) {
        requireOpen();
        long subId = NativeBridge.subscribeFileEvents(runtimeHandle, conferenceId,
                                                       participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }

    /**
     * Subscribe to DTMF events from a SIP participant.
     *
     * @param participantId the SIP participant to subscribe for
     * @param observer      receives {@link DtmfEvent}
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    @SuppressWarnings("unchecked")
    public Subscription streamDtmfEvents(String participantId,
                                          EventStreamObserver<DtmfEvent> observer) {
        requireOpen();
        long subId = NativeBridge.subscribeDtmfEvents(runtimeHandle, conferenceId,
                                                       participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }

    /**
     * Subscribe to trickle-ICE candidates from a WebRTC participant.
     *
     * <p>Candidates must be relayed to the remote browser via the application signaling channel.
     *
     * @param participantId the WebRTC participant to subscribe for
     * @param observer      receives {@link IceCandidateEvent}
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     */
    @SuppressWarnings("unchecked")
    public Subscription streamWebRtcIceCandidates(String participantId,
                                                    EventStreamObserver<IceCandidateEvent> observer) {
        requireOpen();
        long subId = NativeBridge.subscribeWebRtcIceCandidates(runtimeHandle, conferenceId,
                                                                 participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }
}
