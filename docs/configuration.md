# Configuration and logging

You configure a runtime with `JSynConfig.builder()` and pass the result to `new JSyn`.
Every option has a default, so `JSynConfig.builder().build()` works on its own once
`SYNAUSON_LICENSE_KEY` is set. The javadoc of `JSynConfig.Builder` has each option's
full description.

- [An example](#an-example)
- [All options](#all-options)
- [Environment variables](#environment-variables)
- [Logging](#logging)
- [Containers](#containers)

## An example

<!-- snippet: runtime-config -->
```java
JSynConfig config = JSynConfig.builder()
    .stateDir("/var/lib/synauson/state")       // license cache and model timings
    .modelStore("/var/lib/synauson/models")    // downloaded models
    .maxConferences(200)                       // your own caps; unset is unlimited
    .maxParticipantsPerConference(10)
    .rtpPortMin(10000)                         // SIP media ports (the defaults)
    .rtpPortMax(20000)
    .webrtcIcePortRange(40000, 40999)          // WebRTC media ports
    .cpuBudget(8.0)                            // cores this runtime may use
    .sttCapacity(null, null, 20)               // keep the measured pool, cap at 20 streams
    .sttEager(true)                            // allow eager end of turn with STT
    .build();
```

## All options

Licensing and storage ([Licensing and models](licensing-and-models.md)):

| Option | Default | Effect |
|---|---|---|
| `licenseKey(String)` | `$SYNAUSON_LICENSE_KEY` | The license key. Required one way or the other. |
| `licenseFile(String)` | none | A license file to use when the licensing server can't be reached |
| `offline(boolean)` | `false` | Never contact the licensing server |
| `stateDir(String)` | `$SYNAUSON_STATE_DIR`, else the per-user state directory | The cached license file and the model timings (`calibration.json`) |
| `modelStore(String)` | `$SYNAUSON_MODEL_STORE`, else the per-user cache directory | Where models are downloaded and read from |

Limits ([Conferences and participants](conferences.md#limits-and-monitoring)):

| Option | Default | Effect |
|---|---|---|
| `maxConferences(int)` | unlimited | Conferences running at once |
| `maxParticipantsPerConference(int)` | unlimited | Participants in one conference, native participants not counted |

Media ([SIP calls](sip.md), [WebRTC calls](webrtc.md)):

| Option | Default | Effect |
|---|---|---|
| `rtpPortMin(int)`, `rtpPortMax(int)` | 10000, 20000 | The UDP ports SIP participants use |
| `rtpJitterBufferMs(int)` | 200 | The receive jitter buffer of SIP participants |
| `webrtcStunServer(String)` | `""` (none) | The STUN server for WebRTC participants that set none of their own, `stun://host[:port]` |
| `webrtcJitterBufferMs(int)` | 200 | The receive jitter buffer of WebRTC participants |
| `webrtcIcePortRange(int, int)` | any port | The local ports WebRTC participants use |

Capacity ([Capacity and sizing](sizing.md)):

| Option | Default | Effect |
|---|---|---|
| `cpuBudget(Double)` | detected | Cores this runtime may use, fractions allowed. Detected as the smallest of the process's cgroup CPU quota, cpuset, affinity mask and CPU count. |
| `memoryBudget(Long)` | the cgroup's memory limit | Bytes of memory this runtime may use; STT sizes its workers within it |
| `sttCapacity(workers, threads, maxStreams)` | measured at startup | The STT pool: workers, ONNX Runtime threads per worker, and the cap on STT streams. Each `null` keeps the measured value; `maxStreams` 0 turns STT off. |
| `ttsCapacity(workers, threads, maxStreams)` | from the CPU count | The TTS pool: workers, threads per worker, and the cap on utterances synthesised at once; `maxStreams` 0 turns TTS off |
| `recalibrate(boolean)` | `false` | Time the models again at startup instead of reusing the cached timings |

Speech to text behaviour ([Speech to text](speech-to-text.md#settings),
[Voice agents](voice-agents.md#eager-end-of-turn)):

| Option | Default | Effect |
|---|---|---|
| `sttTurnFlush(Boolean)` | off | Close each turn's transcript on a forecast of its last words, as soon as turn detection ends the turn |
| `sttEager(Boolean)` | off | Allow eager end of turn on participants with STT, setting decoding aside for it |

## Environment variables

| Variable | Effect |
|---|---|
| `SYNAUSON_LICENSE_KEY` | The license key, used when `licenseKey` is null |
| `SYNAUSON_MODEL_STORE` | The model store, used when `modelStore` is null. Default: `~/.cache/synauson/models` (or `$XDG_CACHE_HOME/synauson/models`) on Linux, `%LOCALAPPDATA%\synauson\models` on Windows. |
| `SYNAUSON_STATE_DIR` | The state directory, used when `stateDir` is null. Default: `~/.local/state/synauson` (or `$XDG_STATE_HOME/synauson`) on Linux, `%LOCALAPPDATA%\synauson\state` on Windows. |
| `SYNAUSON_LOG_LEVEL` | The engine's log filter ([below](#logging)) |
| `SYNAUSON_LOG_FORMAT` | `json` for JSON lines instead of text |

The Linux defaults need `HOME` (or the `XDG_` variables). A service that runs without a
home directory should set `modelStore` and `stateDir`, or the two variables, explicitly:
without them the model store can't be found, and the state directory falls back to a
temporary directory that doesn't survive restarts.

## Logging

The engine writes its logs to the process's stderr, as compact text or, with
`SYNAUSON_LOG_FORMAT=json`, as JSON lines. jsyn's own few warnings go to
`System.Logger`, under the name `com.synauson.jsyn`.

`SYNAUSON_LOG_LEVEL` sets the filter in `RUST_LOG` syntax: a level such as `info` or
`debug`, or per-module levels. Without it the engine reads `RUST_LOG`, then defaults to
`warn`, so a healthy runtime is quiet. An invalid filter falls back to the default and
says so. The engine reads both variables once, when the natives load, so set them
before the JVM starts.

```bash
SYNAUSON_LOG_LEVEL=debug java -jar your-app.jar
```

When you report a problem, include a log taken with `SYNAUSON_LOG_LEVEL=debug`.

## Containers

- Put the model store and the state directory on volumes, so the models, the cached
  license and the model timings survive restarts.
- Pass the license key in `SYNAUSON_LICENSE_KEY` from a secret.
- The engine sizes itself from the container's CPU quota and memory limit, so give the
  container the limits you mean it to use ([Capacity and sizing](sizing.md)).
- Open the SIP and WebRTC port ranges, or use host networking
  ([Network and firewall](install.md#network-and-firewall)).
- The image needs GStreamer 1.24 or 1.26: base it on Ubuntu 24.04 or Debian 13, and
  install the [packages](install.md#install-gstreamer).
