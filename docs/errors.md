# Errors

Every jsyn error is an unchecked exception that extends `JSynException`, named after
the kind of failure. Catch the class you can act on, and read the message for detail.
The javadoc's `@throws` clauses say which exceptions each method throws.

| Exception | Means | Typical causes |
|---|---|---|
| `InvalidArgumentException` | An argument or a setting is wrong | A required builder field missing (the message names each one), a null argument, an unknown codec or voice, a value out of range, a detector without the one it needs, no license key, a WebRTC offer that isn't SDP with one audio stream, a file the engine can't play, a route from a recording or to a file participant, `INVALID_TURN_CONFIG`, `INVALID_SPEAK` |
| `NotFoundException` | No such conference, participant or file | A participant removed or never added (in a call, a route or a stream subscription), a `file:` URI whose file doesn't exist, a conference ended by `shutdownServer` |
| `AlreadyExistsException` | The id is taken | A conference, participant, SIP reservation or speaker id already in use |
| `FailedPreconditionException` | The call can't run in the current state | A model not downloaded yet, STT or TTS still loading, a speaker on a runtime with TTS turned off, a detector stream on a participant without that detector, priority audio on a participant that isn't a file participant, a route to or from a SIP reservation that hasn't connected, `TTS_REQUIRED`, `UTTERANCE_ENDED`, an illegal license file, no share of a license pool yet (`LICENSE_SHARE_PENDING`, retry later) |
| `PermissionDeniedException` | The license doesn't allow it | A key the licensing server refused (`LICENSE_REJECTED`), no license file that still works, 7 days after the last usage report that got through (`NO_LICENSE`), a runtime that hasn't reached the server for 7 days (`LICENSE_DISCONNECTED`), or an expired developer key (`LICENSE_EXPIRED`) |
| `LimitExceededException` | A limit is reached | Every AI session the license allows in use (`SESSION_LIMIT_REACHED`), every conference the license allows in use (`CONFERENCE_POOL_LIMIT_REACHED`), your own `maxConferences` (`CONFERENCE_LIMIT_REACHED`) or `maxParticipantsPerConference`, the machine's STT stream cap, no free SIP port pair |
| `NativeResourceClosedException` | The object was closed | A call on a `JSyn`, `Conference`, `Subscription` or `NativeParticipant` after `close()` |
| `AgentStreamException` | The agent event stream refused or dropped a subscription | See the reasons below |
| `InternalException` | The engine itself failed; a mistake in your request gets one of the classes above | GStreamer missing an element, a pipeline failure. Treat it as fatal for the call it came from. |
| `UnauthenticatedException` | | Not thrown by jsyn's in-process runtime |

## Reason codes

Every exception's message starts with a stable reason code, then a colon and a
description: `NotFoundException: CONFERENCE_NOT_FOUND: conference 'c1' not found`.
Match on the code, never on the rest of the message, which may be reworded in any
release: every exception's `reason()` returns it (since jsyn 1.7.0), so
`catch (FailedPreconditionException e)` can retry `MODEL_LOADING` and
`LICENSE_SHARE_PENDING` and treat the rest as errors. That holds for the checks jsyn makes itself too: a null argument or a missing
builder field is `INVALID_ARGUMENT`, a call on a closed object `HANDLE_CLOSED`. The codes
are the reasons of synauson's gRPC API, each with its meaning in the
[`ErrorReason` table](https://github.com/synauson/api/blob/main/docs/api.md#errorreason);
jsyn adds `HANDLE_CLOSED` (a call on a closed runtime, conference, participant or
subscription), and `ILLEGAL_LICENSE` and `NO_LICENSE`, which only `new JSyn` throws.

Ids of conferences, participants, speakers and utterances are 1 to 128 characters from
`A-Z`, `a-z`, `0-9`, `.`, `_` and `-`: any other id throws `InvalidArgumentException`
with `INVALID_ID`. A speaker's default id is its participant's id plus `.speaker`, so
adding a participant with a `TtsConfig` and no `speakerId` throws `INVALID_ID` when the
participant id is over 120 characters.

`updateTurnConfig` leads its `InvalidArgumentException` message with
`INVALID_TURN_CONFIG` when it refuses a value
([Voice agents](voice-agents.md#ending-turns-and-the-turn-config)).

`AgentStreamException.reason()` ([Voice agents](voice-agents.md)):

| Reason | Meaning |
|---|---|
| `TURN_DETECTION_REQUIRED` | The participant has no turn detection, so it has no agent stream |
| `AGENT_SUBSCRIBER_LAGGED` | The observer fell 256 events behind and was dropped; `lastSeq()` is the last event it got |
| `AGENT_REPLAY_EXPIRED` | The resume cursor is older than the 512 events the stream keeps |
| `AGENT_STREAM_MISMATCH` | The resume cursor is from a stream the participant no longer has |

Text to speech errors lead their message with a reason, such as
`TTS_REQUIRED: participant 'p' has no speaker: ...`:
`TTS_REQUIRED`, `UTTERANCE_ENDED`, `UTTERANCE_NOT_INTERRUPTIBLE`, `UNKNOWN_UTTERANCE` and
`INVALID_SPEAK` ([Text to speech](text-to-speech.md#errors)).

The agent stream's `Error` event carries `TURN_DETECTION_FAILED`,
`TURN_DECISION_MISSING`, `STT_STOPPED` or `STT_LAGGING`, and `UtteranceFailed` carries
`TTS_UNAVAILABLE`, `SYNTHESIS_FAILED`, `UNSPEAKABLE_TEXT` or `TTS_CAPACITY`. These are
events on a running stream, not exceptions.

## Null safety

The public API is `@NullMarked` (JSpecify): no parameter, return value or field is null
unless it is marked `@Nullable`. Passing null where it isn't allowed throws
`InvalidArgumentException` naming the argument.
