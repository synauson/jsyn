package com.synauson.jsyn.participant;

import com.synauson.jsyn.AgentStreamOptions;
import com.synauson.jsyn.AppliedTurnConfig;
import com.synauson.jsyn.ConferenceState;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.ResourceSnapshot;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.event.DtmfEvent;
import com.synauson.jsyn.event.FileEvent;
import com.synauson.jsyn.event.IceCandidateEvent;
import com.synauson.jsyn.event.TurnDetectionEvent;
import com.synauson.jsyn.event.TranscriptEvent;
import com.synauson.jsyn.event.VadEvent;
import com.synauson.jsyn.internal.Args;
import com.synauson.jsyn.internal.NativeBridge;
import com.synauson.jsyn.internal.NativeParticipantNativeHandle;
import com.synauson.jsyn.internal.NativeResource;
import com.synauson.jsyn.spec.ConnectionMatrix;
import com.synauson.jsyn.spec.FileParticipantSpec;
import com.synauson.jsyn.spec.NativeParticipantSpec;
import com.synauson.jsyn.spec.PriorityFile;
import com.synauson.jsyn.spec.RecordingParticipantSpec;
import com.synauson.jsyn.spec.SipConnectionSpec;
import com.synauson.jsyn.spec.SipParticipantSpec;
import com.synauson.jsyn.spec.SipReservationSpec;
import com.synauson.jsyn.spec.TurnConfigUpdate;
import com.synauson.jsyn.spec.WebRtcParticipantSpec;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code conferenceId} is null
     */
    public Conference(long runtimeHandle, String conferenceId) {
        super(terminator(runtimeHandle, Args.notNull(conferenceId, "conferenceId")));
        this.runtimeHandle = runtimeHandle;
        this.conferenceId = conferenceId;
    }

    // Checked before super() so a null ID never reaches the Cleaner's native call.
    private static Runnable terminator(long runtimeHandle, String conferenceId) {
        return () -> NativeBridge.terminateConference(runtimeHandle, conferenceId);
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code spec} is null
     */
    public FileParticipantHandle addFileParticipant(FileParticipantSpec spec) {
        requireOpen();
        Args.notNull(spec, "spec");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code spec} is null
     */
    public RecordingParticipantHandle addRecordingParticipant(RecordingParticipantSpec spec) {
        requireOpen();
        Args.notNull(spec, "spec");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code spec} is null
     */
    public SipParticipantHandle addSipParticipant(SipParticipantSpec spec) {
        requireOpen();
        Args.notNull(spec, "spec");
        String specJson = GSON.toJson(spec);
        String resultJson = NativeBridge.addSipParticipant(runtimeHandle, conferenceId, specJson);
        JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
        String pid = result.get("participantId").getAsString();
        int localRtpPort = result.get("localRtpPort").getAsInt();
        return new SipParticipantHandle(runtimeHandle, conferenceId, pid, localRtpPort);
    }

    /**
     * Reserve a SIP participant's local RTP/RTCP ports for an outbound call, before the peer's
     * media is known.
     *
     * <p>Put {@link SipReservation#localRtpPort()} in the SDP offer, then call
     * {@link #connectSipParticipant} with the media from the peer's answer. Both sockets are
     * bound when this returns, so media the peer sends early is kept, but nothing runs until
     * the connect. Use {@link #addSipParticipant} instead when the peer's media is already
     * known (an inbound call). Release an unconnected reservation with
     * {@link #removeParticipant(String)}.
     *
     * @param spec the reservation configuration
     * @return the reserved ports
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.AlreadyExistsException if the participant ID is
     *         already a participant or reservation in this conference
     * @throws com.synauson.jsyn.exception.LimitExceededException if no port pair or
     *         participant slot is free
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code spec} is null or
     *         the SRTP key is not 30 bytes
     * @since 1.2.0
     */
    public SipReservation reserveSipParticipant(SipReservationSpec spec) {
        requireOpen();
        Args.notNull(spec, "spec");
        String resultJson = NativeBridge.reserveSipParticipant(runtimeHandle, conferenceId,
                                                               GSON.toJson(spec));
        JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
        return new SipReservation(result.get("participantId").getAsString(),
                                  result.get("localRtpPort").getAsInt(),
                                  result.get("localRtcpPort").getAsInt());
    }

    /**
     * Start a SIP participant reserved with {@link #reserveSipParticipant} on its reserved
     * ports, once the peer's SDP answer is in. The participant sends from the port it receives
     * on (symmetric RTP).
     *
     * <p>Invalid arguments, such as an SRTP key on one side only, leave the reservation in
     * place so the call can be retried. Any later failure releases it, and the participant
     * must be reserved again.
     *
     * @param spec the reserved participant's ID and the peer's negotiated media
     * @return a handle to the running participant, carrying the reserved RTP port
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.NotFoundException if no reservation has this ID
     * @throws com.synauson.jsyn.exception.AlreadyExistsException if the ID belongs to a
     *         participant that is already running
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code spec} is null, the
     *         remote media is invalid, or its SRTP key does not pair with the reservation's
     * @since 1.2.0
     */
    public SipParticipantHandle connectSipParticipant(SipConnectionSpec spec) {
        requireOpen();
        Args.notNull(spec, "spec");
        String resultJson = NativeBridge.connectSipParticipant(runtimeHandle, conferenceId,
                                                               GSON.toJson(spec));
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code spec} is null
     */
    public WebRtcParticipantHandle addWebRtcParticipant(WebRtcParticipantSpec spec) {
        requireOpen();
        Args.notNull(spec, "spec");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} or {@code spec} is null
     */
    public NativeParticipant addNativeParticipant(String participantId, NativeParticipantSpec spec) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(spec, "spec");
        String specJson = GSON.toJson(spec);
        NativeParticipantNativeHandle nativeHandle =
            NativeBridge.addNativeParticipant(runtimeHandle, conferenceId, participantId, specJson);
        return new NativeParticipant(conferenceId, participantId, nativeHandle);
    }

    /**
     * Remove a participant from this conference, or release an unconnected
     * {@link SipReservation} and its ports.
     *
     * @param participantId the ID of the participant to remove
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} is null
     */
    public void removeParticipant(String participantId) {
        requireOpen();
        Args.notNull(participantId, "participantId");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} is null
     */
    public void muteParticipant(String participantId, boolean muted) {
        requireOpen();
        Args.notNull(participantId, "participantId");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code matrix} is null
     */
    public void updatePartyAudioConnections(ConnectionMatrix matrix) {
        requireOpen();
        Args.notNull(matrix, "matrix");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId}, {@code files}, or any element of {@code files} is null
     */
    public void addPriorityAudioFiles(String participantId, List<PriorityFile> files) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(files, "files");
        // Serialize as {"uris": ["...", ...]} matching PriorityAudioFilesJson in Rust.
        List<String> uris = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            uris.add(Args.notNull(files.get(i), "files[" + i + "]").uri);
        }
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} or {@code observer} is null
     */
    @SuppressWarnings("unchecked")
    public Subscription streamVadEvents(String participantId,
                                         EventStreamObserver<VadEvent> observer) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(observer, "observer");
        long subId = NativeBridge.subscribeVadEvents(runtimeHandle, conferenceId,
                                                      participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }

    /**
     * Subscribe to turn detection events for a participant.
     *
     * @param participantId the participant to subscribe for
     * @param observer      receives {@link TurnDetectionEvent.TurnResult}
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} or {@code observer} is null
     */
    @SuppressWarnings("unchecked")
    public Subscription streamTurnDetectionEvents(String participantId,
                                               EventStreamObserver<TurnDetectionEvent> observer) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(observer, "observer");
        long subId = NativeBridge.subscribeTurnDetectionEvents(runtimeHandle, conferenceId,
                                                            participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }

    /**
     * Subscribe to a participant's streaming STT: committed text as it is decoded
     * ({@link TranscriptEvent.Delta}) and each turn's text once turn detection completes the
     * turn ({@link TranscriptEvent.Turn}). Events sent before the subscription are not
     * replayed.
     *
     * @param participantId a participant added with an {@link com.synauson.jsyn.spec.SttConfig}
     * @param observer      receives {@link TranscriptEvent} subtypes
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} or {@code observer} is null
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if the participant
     *         has no STT
     * @since 1.6.0
     */
    @SuppressWarnings("unchecked")
    public Subscription streamTranscriptEvents(String participantId,
                                                EventStreamObserver<TranscriptEvent> observer) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(observer, "observer");
        long subId = NativeBridge.subscribeTranscriptEvents(runtimeHandle, conferenceId,
                                                             participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }

    /**
     * Subscribe to a participant's voice-agent event stream, from every event
     * the stream still keeps. Same as {@link #streamAgentEvents(String,
     * AgentStreamOptions, EventStreamObserver)} with {@link AgentStreamOptions#defaults()}.
     *
     * @param participantId a participant added with a
     *                      {@link com.synauson.jsyn.spec.TurnDetectionConfig}
     * @param observer      receives {@link AgentEvent}s
     * @return a {@link Subscription} that cancels the stream when closed
     * @throws com.synauson.jsyn.exception.AgentStreamException with reason
     *         {@code TURN_DETECTION_REQUIRED} if the participant has no turn detection
     * @throws com.synauson.jsyn.exception.NotFoundException if there is no such participant
     * @since 1.6.0
     */
    public Subscription streamAgentEvents(String participantId,
                                          EventStreamObserver<AgentEvent> observer) {
        return streamAgentEvents(participantId, AgentStreamOptions.defaults(), observer);
    }

    /**
     * Subscribe to a participant's voice-agent event stream: one ordered stream
     * of {@link AgentEvent}s for an agent that talks with the participant, which needs a
     * {@link com.synauson.jsyn.spec.TurnDetectionConfig} (and the VAD that drives it).
     *
     * <p>{@link AgentEvent.Subscribed} comes first, then the events the stream keeps after
     * the cursor in {@code options} (all it keeps, its last 512, without one), then live
     * events, with an {@link AgentEvent.Heartbeat} whenever nothing else came for the
     * heartbeat interval. {@link AgentEvent.StreamEnded} is last, when the participant or
     * the conference goes, and {@code onCompleted} follows. Kinds a newer engine adds
     * (eager end of turn) reach this jsyn as {@link AgentEvent.Unknown}.
     *
     * <p><b>Lag and resume.</b> The observer runs on an engine thread and the engine never
     * waits for it: an observer 256 events behind is dropped, and {@code onError} gets an
     * {@link com.synauson.jsyn.exception.AgentStreamException} with reason
     * {@code AGENT_SUBSCRIBER_LAGGED} and the last seq it was given. To resume without
     * losing or repeating an event, keep the last stored event you processed and
     * subscribe again from it:
     *
     * <pre>{@code
     * void onError(Throwable t) {
     *     if (t instanceof AgentStreamException) {
     *         AgentStreamException e = (AgentStreamException) t;
     *         AgentStreamOptions from = e.reason().equals(AgentStreamException.AGENT_SUBSCRIBER_LAGGED)
     *             ? AgentStreamOptions.resumeAfter(lastStored)   // the last stored event handled
     *             : AgentStreamOptions.defaults();
     *         // Subscribe again off the engine thread, e.g. on your executor.
     *         executor.execute(() -> conf.streamAgentEvents(pid, from, this));
     *     }
     * }
     * }</pre>
     *
     * A resume whose cursor the stream no longer keeps throws {@code AGENT_REPLAY_EXPIRED},
     * and one from a stream the participant no longer has (it was added again) throws
     * {@code AGENT_STREAM_MISMATCH}: subscribe again with
     * {@link AgentStreamOptions#defaults()}. Hand events to your own queue rather than
     * working in {@code onNext}, so the observer keeps up.
     *
     * @param participantId a participant added with a
     *                      {@link com.synauson.jsyn.spec.TurnDetectionConfig}
     * @param options       where to start and the heartbeat interval
     * @param observer      receives {@link AgentEvent}s
     * @return a {@link Subscription} that cancels the stream when closed
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if an argument is null,
     *         or the cursor is past the stream's newest event
     * @throws com.synauson.jsyn.exception.AgentStreamException with reason
     *         {@code TURN_DETECTION_REQUIRED}, {@code AGENT_REPLAY_EXPIRED} or
     *         {@code AGENT_STREAM_MISMATCH}
     * @throws com.synauson.jsyn.exception.NotFoundException if there is no such participant
     * @since 1.6.0
     */
    public Subscription streamAgentEvents(String participantId, AgentStreamOptions options,
                                          EventStreamObserver<AgentEvent> observer) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(options, "options");
        Args.notNull(observer, "observer");
        long subId = NativeBridge.subscribeAgentEvents(runtimeHandle, conferenceId, participantId,
            options.afterSeq(), options.streamId(), options.heartbeatMs(),
            new AgentEventParser(observer));
        return new Subscription(subId);
    }

    /**
     * End the participant's open voice-agent turn now. Its
     * {@link AgentEvent.EndOfTurn} (reason {@code MANUAL}) follows on the agent stream: at
     * once without STT; with STT once the turn's transcript settles, which the engine
     * speeds up by forecasting its last words. If the participant is still speaking, the
     * next turn starts at once.
     *
     * @param participantId a participant added with a
     *                      {@link com.synauson.jsyn.spec.TurnDetectionConfig}
     * @return the id of the turn that ends, or empty when no turn was open or, with STT,
     *         the turn had no word yet (a turn is on the stream from its first word; this
     *         one is sent, with its {@code EndOfTurn}, only if STT then finds words in it)
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if {@code participantId} is null
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if the participant
     *         has no turn detection
     * @throws com.synauson.jsyn.exception.NotFoundException if there is no such participant
     * @since 1.6.0
     */
    public OptionalLong forceEndTurn(String participantId) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        String json = NativeBridge.forceEndTurn(runtimeHandle, conferenceId, participantId);
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        boolean ended = o.has("ended") && o.get("ended").getAsBoolean();
        return ended && o.has("turnId") ? OptionalLong.of(o.get("turnId").getAsLong()) : OptionalLong.empty();
    }

    /**
     * Change how the participant's voice-agent turns end. The fields set in
     * {@code update} replace the current ones; a
     * {@link AgentEvent.TurnConfigUpdated} announces the result on the agent stream. The
     * end-of-turn threshold also decides {@link TurnDetectionEvent.TurnResult#turnComplete}.
     *
     * @param participantId a participant added with a
     *                      {@link com.synauson.jsyn.spec.TurnDetectionConfig}
     * @param update        the changes
     * @return the config now in effect and the seq of its {@code TurnConfigUpdated}
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if an argument is null
     *         or a value is out of range (the config is then unchanged)
     * @throws com.synauson.jsyn.exception.FailedPreconditionException if the participant
     *         has no turn detection
     * @throws com.synauson.jsyn.exception.NotFoundException if there is no such participant
     * @since 1.6.0
     */
    public AppliedTurnConfig updateTurnConfig(String participantId, TurnConfigUpdate update) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(update, "update");
        String json = NativeBridge.updateTurnConfig(runtimeHandle, conferenceId, participantId,
            GSON.toJson(update));
        return AppliedTurnConfig.fromJson(json);
    }

    /** Turns the engine's JSON strings into {@link AgentEvent}s for the caller's observer. */
    private static final class AgentEventParser implements EventStreamObserver<String> {
        private final EventStreamObserver<AgentEvent> observer;

        AgentEventParser(EventStreamObserver<AgentEvent> observer) {
            this.observer = observer;
        }

        @Override
        public void onNext(String json) {
            AgentEvent event;
            try {
                event = AgentEvent.fromJson(json);
            } catch (RuntimeException e) {
                System.getLogger("com.synauson.jsyn")
                    .log(System.Logger.Level.WARNING, "unreadable agent event dropped", e);
                return;
            }
            observer.onNext(event);
        }

        @Override
        public void onError(Throwable t) { observer.onError(t); }

        @Override
        public void onCompleted() { observer.onCompleted(); }
    }

    /**
     * Subscribe to file playback events for a participant.
     *
     * @param participantId the participant to subscribe for
     * @param observer      receives {@link FileEvent} subtypes
     * @return a {@link Subscription} that cancels the stream when {@link Subscription#close()} is called
     * @throws com.synauson.jsyn.exception.NativeResourceClosedException if this conference is closed
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} or {@code observer} is null
     */
    @SuppressWarnings("unchecked")
    public Subscription streamFileEvents(String participantId,
                                          EventStreamObserver<FileEvent> observer) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(observer, "observer");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} or {@code observer} is null
     */
    @SuppressWarnings("unchecked")
    public Subscription streamDtmfEvents(String participantId,
                                          EventStreamObserver<DtmfEvent> observer) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(observer, "observer");
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
     * @throws com.synauson.jsyn.exception.InvalidArgumentException if
     *         {@code participantId} or {@code observer} is null
     */
    @SuppressWarnings("unchecked")
    public Subscription streamWebRtcIceCandidates(String participantId,
                                                    EventStreamObserver<IceCandidateEvent> observer) {
        requireOpen();
        Args.notNull(participantId, "participantId");
        Args.notNull(observer, "observer");
        long subId = NativeBridge.subscribeWebRtcIceCandidates(runtimeHandle, conferenceId,
                                                                 participantId, (EventStreamObserver<?>) observer);
        return new Subscription(subId);
    }
}
