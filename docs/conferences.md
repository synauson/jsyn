# Conferences and participants

This page explains how a jsyn application is put together: one runtime, conferences,
the participants in them, and the audio routes between participants.

- [The runtime](#the-runtime)
- [Conferences](#conferences)
- [Participants](#participants)
- [Routing audio](#routing-audio)
- [Play a file](#play-a-file)
- [Record a participant](#record-a-participant)
- [Mute and announcements](#mute-and-announcements)
- [Events and threads](#events-and-threads)
- [Closing and cleanup](#closing-and-cleanup)
- [Limits and monitoring](#limits-and-monitoring)

## The runtime

`JSyn` is the runtime. `new JSyn(config)` loads the natives, checks that GStreamer has
the elements the engine needs, checks the license key, and starts downloading models in
the background. Create one per process and share it: GStreamer and ONNX Runtime are
process-wide, so a second instance gains you nothing. [Configuration and
logging](configuration.md) lists every option.

<!-- snippet: runtime -->
```java
try (JSyn jsyn = new JSyn(JSynConfig.builder().build())) {
    // start conferences here
}
```

## Conferences

A conference is a set of participants and the audio routes between them.
`jsyn.startConference(id)` starts one and returns a `Conference`. Conference ids are
unique within the runtime: reusing a live one throws `AlreadyExistsException`.

`conf.state()` returns the conference's participants (id and kind) and when it was
created. `conf.close()`, or `conf.terminate()`, ends the conference and removes every
participant in it.

`jsyn.shutdownServer()` ends every conference in the runtime, giving them five seconds;
`shutdownServer(Duration)` takes another grace period. It returns at once and the
conferences end in the background. Their `Conference` handles then refuse calls with
`NotFoundException`, and closing them is quiet. New conferences can still start.

## Participants

You add a participant with one of the `conf.add*Participant(spec)` methods. Each spec
has a builder, and `build()` throws `InvalidArgumentException` naming every required
field you left out. Options you leave unset take the defaults given in the javadoc.
Participant ids are unique within a conference: an id already in use, by a participant,
a SIP reservation or a speaker, throws `AlreadyExistsException`.

| Participant | Spec | Use it for | Guide |
|---|---|---|---|
| File | `FileParticipantSpec` | Playing an audio file, once or in a loop: prompts, hold music | [below](#play-a-file) |
| Recording | `RecordingParticipantSpec` | Writing one participant's audio to a WAV file | [below](#record-a-participant) |
| SIP | `SipParticipantSpec`, or `SipReservationSpec` then `SipConnectionSpec` | An RTP media leg of a phone call, with DTMF and optional SRTP | [SIP calls](sip.md) |
| WebRTC | `WebRtcParticipantSpec` | A browser or other WebRTC peer | [WebRTC calls](webrtc.md) |
| Native | `NativeParticipantSpec` | Audio your Java code sends and receives | [Native participants](native-participants.md) |

File, SIP, WebRTC and native participants can also carry AI: voice activity and turn
detection ([VadConfig and TurnDetectionConfig](detection.md)), speech to text
([SttConfig](speech-to-text.md)) and a speaker for text to speech
([TtsConfig](text-to-speech.md)). Each needs the one before it on the same participant:

```text
VAD  <-  turn detection  <-  speech to text
                         <-  speaker (text to speech)
```

`conf.removeParticipant(id)` removes a participant, along with its detectors and event
streams.

## Routing audio

Adding a participant connects nothing. Audio flows only along the one-way connections
you set with `conf.updatePartyAudioConnections`, and each call replaces the whole set of
connections:

<!-- snippet: routing -->
```java
// alice hears bob and bob hears alice: two one-way connections.
conf.updatePartyAudioConnections(new ConnectionMatrix(
    ConnectionEntry.connect("alice", "bob"),
    ConnectionEntry.connect("bob", "alice")));

// Later, carol joins and hears both; bob stops hearing alice for now.
// Each call replaces the whole matrix, so list every connection you want.
conf.updatePartyAudioConnections(new ConnectionMatrix(
    ConnectionEntry.connect("alice", "carol"),
    ConnectionEntry.connect("bob", "carol"),
    ConnectionEntry.connect("bob", "alice"),
    ConnectionEntry.muted("alice", "bob")));
```

- A connection goes from a source to a destination. A destination with several sources
  hears them mixed.
- `ConnectionEntry.muted(source, dest)` keeps the route but carries silence.
- `ConnectionMatrix.empty()` removes every connection.
- The engine checks every entry before it changes anything, so a matrix it refuses
  leaves the routing as it was. A participant that isn't in the conference throws
  `NotFoundException`; a recording as a source, or a file participant as a destination,
  throws `InvalidArgumentException`; a SIP reservation that hasn't connected throws
  `FailedPreconditionException`.
- A participant's speaker is a source of its own, `<participant id>.speaker` by
  default; see [Text to speech](text-to-speech.md#routing-the-speaker).

Detectors and speech to text listen to what a participant sends into the conference, so
they work whether or not anything is routed from it.

## Play a file

A file participant plays a file, given as a URI, into the conference, from its start and
in real time from the moment you add it. It plays WAV (PCM, A-law or mu-law), Ogg Opus,
Ogg Vorbis, FLAC and MP3, and refuses any other format with `InvalidArgumentException`,
whatever GStreamer plugins the machine has. Build the URI with `Path.toUri()`, which
gives a valid `file:///` URI on Windows too:

<!-- snippet: play-a-file -->
```java
conf.addFileParticipant(FileParticipantSpec.builder()
    .id("prompt")
    .uri(Path.of("prompt.wav").toUri().toString())
    .loopPlayback(false)   // the default; true plays it forever
    .build());
```

`conf.streamFileEvents(id, observer)` reports `FileEvent.PlaybackStarted`, then
`FileEvent.Eos` when the file ends. You can subscribe before you add the participant,
which is how the [quickstart](../README.md#quickstart) makes sure it sees
`PlaybackStarted`. A `file:` URI whose file doesn't exist makes the add throw
`NotFoundException`. A file the engine can't play sends `FileEvent.FileError` with the
reason, either at the add, which also throws `InvalidArgumentException`, or while it
plays. Wait for `Eos` or `FileError`, as the quickstart does.

## Record a participant

A recording participant writes what one participant sends into the conference to a WAV
file. Removing the recorder closes the file. This program plays a file and records it:

<!-- snippet: record-a-file -->
```java
public static void main(String[] args) throws Exception {
    Path input = Path.of(args[0]);   // a WAV file to play
    Path output = Path.of(args[1]);  // where to write the recording

    try (JSyn jsyn = new JSyn(JSynConfig.builder().build()); // key from SYNAUSON_LICENSE_KEY
         Conference conf = jsyn.startConference("recording-demo")) {
        CountDownLatch played = new CountDownLatch(1);
        try (Subscription events = conf.streamFileEvents("prompt", event -> {
            if (event instanceof FileEvent.Eos || event instanceof FileEvent.FileError) {
                played.countDown();
            }
        })) {
            conf.addFileParticipant(FileParticipantSpec.builder()
                .id("prompt")
                .uri(input.toUri().toString())
                .build());
            // The recorder writes what "prompt" sends into the conference.
            conf.addRecordingParticipant(RecordingParticipantSpec.builder()
                .id("recorder")
                .sourceParticipantId("prompt")
                .outputPath(output.toAbsolutePath().toString())
                .build());
            played.await(60, TimeUnit.SECONDS);
        }
        conf.removeParticipant("recorder"); // closes the WAV file
    }
    System.out.println("recorded " + output);
}
```

To record both sides of a call, add one recorder for each participant.

## Mute and announcements

<!-- snippet: announcements -->
```java
conf.addFileParticipant(FileParticipantSpec.builder()
    .id("hold-music")
    .uri(holdMusic.toUri().toString())
    .loopPlayback(true)
    .build());

// Play a notice over the music; the music resumes when it ends.
conf.addPriorityAudioFiles("hold-music",
    List.of(new PriorityFile(notice.toUri().toString())));

// Silence every route from a participant, then let it be heard again.
conf.muteParticipant("hold-music", true);
conf.muteParticipant("hold-music", false);
```

- `addPriorityAudioFiles` works on file participants; on any other it throws
  `FailedPreconditionException`. It plays each file whole, in order, in place of the
  participant's own file, which pauses and then resumes where it paused. Files from a
  later call queue behind those still playing. Each file's `PlaybackStarted` and
  `PlaybackEnded` (with its URI) arrive on `streamFileEvents`. A file that can't be
  played sends a `FileError` there and is skipped.
- `muteParticipant(id, true)` silences every route from the participant, including
  routes added while it is muted, until you unmute it; unmuting gives each route back
  its own `ConnectionEntry.muted`. Any participant can be muted, native ones included.
  Its own detectors and speech to text still hear it.

## Events and threads

Every `conf.stream*` method takes an `EventStreamObserver` and returns a `Subscription`.
`onNext` is the only method you must write; `onError` logs a warning by default, and
`onCompleted` does nothing.

| Stream | Events | Guide |
|---|---|---|
| `streamFileEvents` | `FileEvent` | [above](#play-a-file) |
| `streamDtmfEvents` | `DtmfEvent` | [SIP calls](sip.md#dtmf) |
| `streamWebRtcIceCandidates` | `IceCandidateEvent` | [WebRTC calls](webrtc.md) |
| `streamVadEvents`, `streamTurnDetectionEvents` | `VadEvent`, `TurnDetectionEvent` | [Detection](detection.md) |
| `streamTranscriptEvents` | `TranscriptEvent` | [Speech to text](speech-to-text.md) |
| `streamAgentEvents` | `AgentEvent` | [Voice agents](voice-agents.md) |

Observers run on an engine thread. Don't block in them and don't throw from them: hand
the event to your own queue or executor and return. Subscribe to a detector's events
after you add the participant, since the detector belongs to the participant.

`Conference` methods are thread-safe. A `NativeParticipant` is the exception: at any
time, at most one thread may call `write` and one thread may call `read`.

## Closing and cleanup

Everything that owns native resources is `AutoCloseable`: `JSyn`, `Conference`,
`Subscription` and `NativeParticipant`. Close them in the reverse order you created
them, which try-with-resources does for you: subscriptions, then participants, then the
conference, then the runtime. `close()` can be called more than once, and a closed
object throws `NativeResourceClosedException` when used. An object you forget to close
is freed when it is garbage collected, which can be much later.

Closing a conference, or removing a participant, ends the participant in the engine but
doesn't close its `NativeParticipant` object: close that yourself, after stopping the
threads that read and write it.

Closing the runtime ends every conference still open first, waiting up to five seconds,
so a recording in progress is a complete WAV file.

Closing the runtime while speech to text or text to speech is still loading stops the
load and waits for any step still running. That can take a few seconds, longer the
first time a model is prepared.

## Limits and monitoring

- `JSynConfig.Builder.maxConferences(n)` caps concurrent conferences, and
  `maxParticipantsPerConference(n)` caps participants in each (native participants
  don't count; a SIP reservation does until it connects or is released). Going over
  either throws `LimitExceededException`. Both are unlimited by default.
- `jsyn.getResourceSnapshot()` returns the process's memory and threads, the cgroup's
  memory limit and use, and each conference with its participant count.
- Each SIP and WebRTC handle has `stats()`, and each `NativeParticipant` has `stats()`.

[Errors](errors.md) lists the exceptions these calls throw.
