# Licensing and models

Every jsyn runtime runs under a license key from Synauson. The license decides which AI
features new work may use and how many AI sessions may run at once. The engine
downloads the models those features need when the runtime starts.

The [End User License Agreement](https://synauson.com/legal/eula/) is the binding text,
and [How licensing connects](https://synauson.com/licensing/) summarises what each runtime
sends to Synauson, how often, and what happens when the connection drops. This page
covers what that means for a jsyn runtime.

- [Set up a runtime](#set-up-a-runtime)
- [How a license works](#how-a-license-works)
- [Plans and AI sessions](#plans-and-ai-sessions)
- [License states](#license-states)
- [Check what the runtime may use](#check-what-the-runtime-may-use)
- [Models](#models)
- [Run without internet access](#run-without-internet-access)
- [Containers and Kubernetes](#containers-and-kubernetes)
- [Errors](#errors)

The [licensing example](https://github.com/synauson/examples/tree/main/java/jsyn-licensing)
has a tour that checks each behaviour on this page against a live runtime, and a
health check you can copy.

## Set up a runtime

A runtime needs the key and two directories that survive restarts:

<!-- snippet: license-config -->
```java
JSynConfig config = JSynConfig.builder()
    // Leave it out (or pass null) to read $SYNAUSON_LICENSE_KEY.
    .licenseKey(System.getenv("SYNAUSON_LICENSE_KEY"))
    // The cached license file and model timings. Keep it across restarts.
    .stateDir("/var/lib/synauson/state")
    // The downloaded models. Keep it across restarts too.
    .modelStore("/var/lib/synauson/models")
    .build();
```

| `JSynConfig.Builder` | When unset | Default |
|---|---|---|
| `licenseKey` (required) | `$SYNAUSON_LICENSE_KEY` | none: `new JSyn` throws `InvalidArgumentException` |
| `stateDir` | `$SYNAUSON_STATE_DIR` | `~/.local/state/synauson` (or `$XDG_STATE_HOME/synauson`) on Linux, `%LOCALAPPDATA%\synauson\state` on Windows |
| `modelStore` | `$SYNAUSON_MODEL_STORE` | `~/.cache/synauson/models` (or `$XDG_CACHE_HOME/synauson/models`) on Linux, `%LOCALAPPDATA%\synauson\models` on Windows |
| `licenseFile` | | none |
| `offline` | | `false` |

On license grounds, `new JSyn` throws only when the key is missing or malformed
(`InvalidArgumentException`), when the licensing server refuses it
(`PermissionDeniedException`), or when the license file doesn't fit this engine
(`FailedPreconditionException`, `ILLEGAL_LICENSE`). It waits up to 10 seconds for
`usage.synauson.com`. If the service can't be reached, the runtime still starts on a
cached license file that still works.
[Errors](errors.md) lists what else `new JSyn` can throw.

## How a license works

At startup the runtime renews its license through `usage.synauson.com`, which fetches
a license file from `license.synauson.com` for it: a certificate signed by Synauson
that names the license's AI session pool. The runtime checks the signature with a
public key built into the engine, caches the file as `license.lic` in the state
directory, and renews it about once a day. The licensing server's answers are signed
too, so neither a spoofed server nor anything in between can reject or upgrade a key.

### One pool for all your runtimes

One key can run any number of runtimes and servers on any number of machines, and
they share the license's pool: `capabilities().limitsScope` is `"all instances of this
license"`. Each runtime reports its AI session counts to `usage.synauson.com` every 30
seconds and gets back its share of the pool, sized by what it uses, with the burst on
the purchased part of it. Participants are still admitted inside the runtime, with
nothing on the network in the way, and a runtime alone gets the whole pool.
`capabilities().usageService` shows the share, the license-wide count and when the 7
days below end.

A report holds counts only: a random id per process, sessions in use and per-minute
peaks, admitted and refused counts, the version. No audio, transcripts, conference or
participant ids, or hostnames. Each report is logged at DEBUG.

**Every runtime must be able to reach `usage.synauson.com` over HTTPS.** If it can't,
nothing stops: it keeps admitting against the last share it held, with its burst,
for 7 days after the last report that got through, also across restarts that keep
the state directory. If the outage is on Synauson's side, Synauson posts a signed
notice on `status.synauson.com` and the 7 days stop counting until the service is
back, for up to 30 days from the runtime's last renewal. The pause reaches only runtimes that can read `status.synauson.com` and whose
clock is within 10 minutes of the notice's; any other runtime recovers in full at its
next report that gets through. After an hour without
a report the state is `offline` and the runtime logs a warning, and from day 3 an
error every day. After the 7 days, on any plan, the state is `disconnected`: no new
conference or participant starts (`PermissionDeniedException`) until a report gets
through, and calls in progress continue. A `licenseFile` you configure is held to the
same 7 days.

`new JSyn` throws `PermissionDeniedException` when there is no license file that still
works: `usage.synauson.com` can't be reached (or `offline` is set) and the cached or
configured file is missing, for another key, or more than 7 days past the last report
that got through. Hosts that can never reach Synauson need an air-gapped license
([Run without internet access](#run-without-internet-access)).

**Keep the state directory.** A new runtime, or one started without its state
directory, has no license file of its own, so it can't start until it reaches
`license.synauson.com` and `usage.synauson.com`, even while an outage on Synauson's
side is paused for everyone else. Give every runtime a `stateDir` that survives
restarts, especially where runtimes scale out or are replaced automatically.

## Plans and AI sessions

Every license has every AI feature on every session, and conferencing without AI.
Licenses differ only in how many AI sessions they allow at once.

| Code | Feature |
|---|---|
| `FEATURE_VAD` | Voice activity detection |
| `FEATURE_TURN_DETECTION` | End-of-turn detection |
| `FEATURE_STT` | Speech to text |
| `FEATURE_TTS` | Text to speech |

`capabilities().license.plan` is `all`, or `none` while the license starts nothing new
(`rejected`, `disconnected`).

The session pool is the **Free base** of 2 sessions plus the sessions the license buys
(`capabilities().sessions.base` and `purchased`). A **Free key** buys none: 2 sessions
with every AI feature, no burst, conferencing without limit, for production use, with
no expiry. A participant with any detector or a speaker takes one session, however many
features it uses. Conferences and participants without AI take none. The pool is
checked only when a participant with AI is added:

- From 80% of the limit the engine logs a warning for each new session.
- Above the limit, a burst on the purchased sessions (25% unless the license sets
  another; none on the base) is still admitted and logged as overage. 10 purchased
  sessions allow 12, up to 15.
- Past the burst, adding a participant with AI throws `LimitExceededException`.

Calls already running are never stopped, whatever happens to the license or to the
licensing server.

A license with an expiry date warns in the log every day for its last 14 days (state
`expiring`). When it expires it runs on the Free plan (state `expired`): a third
participant with AI at once throws `LimitExceededException`, everything else still
starts, and calls in progress continue, even past the Free plan's two sessions. A
restart starts on the Free plan too. A runtime that is offline when its paid plan
expires admits no new AI sessions until it reconnects. Renewing the license restores the
full plan at the next daily renewal, without a restart.

## License states

`capabilities().license.state` reports where the license stands:

| State | Meaning |
|---|---|
| `valid` | Running on a license file. `source` says whether this runtime checked it out (`server`), is using its cached copy while the server is unreachable (`cache`), or is using the `licenseFile` you configured (`provided`). |
| `expiring` | The license expires within 14 days. `daysRemaining` counts down. |
| `offline` | No usage report has got through for an hour or more. Everything works until 7 days after the last one that did (paused while Synauson's outage notice is up); `daysRemaining` counts down. |
| `expired` | The license has expired: the Free plan, 2 AI sessions. |
| `disconnected` | No usage report has got through for 7 days. New conferences and participants are refused with `PermissionDeniedException` until one does, and calls in progress continue. |
| `invalid-kept-last-valid` | A renewal brought a license file the runtime refused. The last valid one stays in force, and `problem` says why. |
| `rejected` | The server refused the license while the runtime was running. New conferences and participants are refused with `PermissionDeniedException`, and calls in progress continue. |

## Check what the runtime may use

`jsyn.capabilities()` reports the license, the session pool, each capability and each
model. It never touches the network, so you can poll it from a health endpoint. This
program prints the main parts:

<!-- snippet: capabilities -->
```java
public static void main(String[] args) throws Exception {
    try (JSyn jsyn = new JSyn(JSynConfig.builder().build())) { // key from SYNAUSON_LICENSE_KEY
        Capabilities caps = jsyn.capabilities(); // never touches the network

        System.out.println("license: " + caps.license.state + ", plan " + caps.license.plan
            + " (" + caps.license.description + ")");
        if (caps.sessions != null) {
            System.out.println("sessions: " + caps.sessions.inUse + " of "
                + (caps.sessions.limit == null ? "unlimited" : caps.sessions.limit)
                + ", level " + caps.sessions.level);
        }
        for (Capabilities.CapabilityInfo c : caps.capabilities) {
            System.out.println(c.code + (c.entitled ? " included" : " not included"));
        }
        for (Capabilities.ModelInfo m : caps.models) {
            System.out.println("model " + m.id + ": " + m.state);
        }
    }
}
```

With a Free key on a machine that has the detection models but not yet the speech ones,
it prints something like:

```text
license: valid, plan all (license '…' (…), perpetual (every AI feature, Free plan, 2 concurrent AI sessions; file from server, works until …))
sessions: 0 of 2, level ok
FEATURE_VAD included
FEATURE_TURN_DETECTION included
FEATURE_STT included
FEATURE_TTS included
model sentito-1: ready
model fermata-1: ready
model spartito-1: downloading
model lettura-2: missing
```

| Field | What it says |
|---|---|
| `license` | `state` (above), `plan`, `source`, `fileExpiry`, `licenseExpiry`, `daysRemaining`, `problem`, and a one-line `description` |
| `sessions` | `limit` (null is unlimited), `inUse`, `ceiling` (the limit with its burst), `peak` since the runtime started, and `level`: `ok`, `near-limit` (from 80%), `overage` (in the burst) or `full` |
| `capabilities` | Each code with `entitled`, and `includedBy`: the entitlement code that grants it when the license doesn't name it itself |
| `models` | Each model's `id`, `release` and `state` (below) |
| `stt`, `tts` | Whether speech to text and text to speech are loaded, and their capacity ([Capacity and sizing](sizing.md)) |

The examples repository's
[`LicenseHealth`](https://github.com/synauson/examples/blob/main/java/jsyn-licensing/src/main/java/com/example/licensing/LicenseHealth.java)
turns the report into OK, DEGRADED or FAILING with reasons, for a health endpoint.

## Models

The engine uses four models, named by codename:

| Model | Role | Download |
|---|---|---|
| `sentito-1` | Voice activity detection | together with `fermata-1`, about 11 MB |
| `fermata-1` | Turn detection | |
| `spartito-1` | Speech to text | about 660 MB |
| `lettura-2` | Text to speech, with its voices and pronunciation data | about 372 MB |

When the runtime starts, it downloads every model into the model store,
in the background, and verifies each file's size and SHA-256. A download that fails is
retried on its own. Runtimes that share a store download each model once. The first speech to text
start also writes a file of shared model weights (about 1.2 GB) into the store; see
[Capacity and sizing](sizing.md#memory).

`capabilities().models` gives each model's state: `ready`, `downloading`, `missing`,
`invalid` or `not-entitled`, with a `detail` that says why it isn't ready. Adding a
participant whose detector needs a model that isn't ready throws
`FailedPreconditionException` naming the model. So either wait for `ready` before you
add AI participants, as the [detection example](detection.md#an-example) does, or catch
that exception and add the participant without AI.

Each model's license notice comes with its download, as `<model id>-NOTICE.txt` in the
store. Pass a model's notice on if you redistribute the model.
`JSyn.modelNotices` returns them, checked against the release, without a running
runtime:

<!-- snippet: model-notices -->
```java
Map<String, String> notices = JSyn.modelNotices(store); // or null for the default store
notices.forEach((model, text) -> System.out.println(model + ":\n" + text));
```

## Run without internet access

On a host that can't reach Synauson or Cloudflare R2, arrange an air-gapped license
with Synauson, give the runtime its license file and fill the model store yourself:

<!-- snippet: offline -->
```java
// Copy model files (sentito-1.onnx, sentito-1-NOTICE.txt, ...) from a connected
// machine, then fill the store. Safe to run on every start.
List<String> installed = JSyn.importModels(modelFiles, Path.of("/var/lib/synauson/models"));

JSyn jsyn = new JSyn(JSynConfig.builder()
    .offline(true)                          // never contact the licensing server
    .licenseFile(licenseFile.toString())    // the air-gapped license.lic for this key
    .modelStore("/var/lib/synauson/models")
    .build());
```

- An air-gapped license file comes from Synauson, with a long lifetime set on the
  order form. It makes no request to Synauson at all, model downloads included, even
  without `offline(true)`: a model you haven't imported fails with an error that says
  to import it. It sends no usage reports, counts its pool on each runtime, and works
  until its `fileExpiry`; replace it before then. A file works only with the key it
  was checked out with. Any other license file with `offline(true)` sends no reports
  and works for 7 days after the last report that got through.
- `JSyn.importModels(from, store)` copies each model whose files are in `from` into
  the store, after checking their size and SHA-256. It returns the ids of the models
  from `from` that are now installed, including any that already were; those are left
  alone. A model with only some of its files in `from` fails the whole call, and a
  folder with no model files throws `InvalidArgumentException`. Instead of importing,
  you can copy a whole model store from a connected machine.

A connected runtime also uses a configured `licenseFile` whenever it can't reach the
server, which covers a first start during an outage.

## Containers and Kubernetes

- Pass the key as `SYNAUSON_LICENSE_KEY` from a Secret.
- Mount volumes at the state directory and the model store, so the license, the models
  and the [model timings](sizing.md#how-the-engine-measures-the-machine) survive
  restarts.
- Don't bake `license.lic` into an image: it contains the key.
- New instances start while the licensing server is down only with a cached or
  configured license file from the last 7 days (keep the state directory on a volume).

## Errors

| Exception | When | What to do |
|---|---|---|
| `InvalidArgumentException` from `new JSyn` | The key is missing or malformed | Set the key |
| `PermissionDeniedException` from `new JSyn` | The server refused the key (unknown, suspended or banned), or there is no license file that still works (offline, or `usage.synauson.com` unreachable for more than 7 days since the last report that got through). An expired license is not refused: it runs on the Free plan. | Check the key and access to `usage.synauson.com`, or contact Synauson |
| `FailedPreconditionException` from `new JSyn`, starting `ILLEGAL_LICENSE` | The license file doesn't fit this engine. The message lists every problem. | Send the message to Synauson for a corrected license |
| `PermissionDeniedException` adding a participant, or from `startConference` | The license was rejected, or no usage report has got through for 7 days (`disconnected`) | Contact Synauson, or restore access to `usage.synauson.com` |
| `LimitExceededException` adding a participant | Every AI session and the burst are in use; or every STT stream this machine admits is in use; or TTS is turned off on this runtime; or your own `maxParticipantsPerConference` is reached. From `startConference`: `maxConferences` is reached. The message says which. | Add it without AI, retry later, or raise the limit |
| `FailedPreconditionException` adding a participant, naming a model | The model isn't downloaded yet | Wait for `ready` in `capabilities().models` |

When there is no room for AI, you decide what the call does. This joins the
participant without it, whichever limit refused it:

<!-- snippet: no-room-for-ai -->
```java
NativeParticipantSpec withVad = NativeParticipantSpec.builder()
    .format(NativeAudioFormat.PCM_S16LE16K_MONO)
    .vad(VadConfig.defaults())
    .build();
NativeParticipantSpec withoutAi = NativeParticipantSpec.builder()
    .format(NativeAudioFormat.PCM_S16LE16K_MONO)
    .build();
try {
    return conf.addNativeParticipant(id, withVad);
} catch (LimitExceededException | PermissionDeniedException e) {
    // Every AI session is in use: join without AI.
    System.out.println(id + " joins without AI: " + e.getMessage());
    return conf.addNativeParticipant(id, withoutAi);
}
```
