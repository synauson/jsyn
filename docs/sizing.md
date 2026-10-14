# Capacity and sizing

All inference runs on the CPU. Speech to text is by far the heaviest thing the engine
runs, so the number of STT streams decides how many calls a machine carries. The engine
measures the machine when it starts and caps STT at what the machine transcribes in real
time.

- [What a call costs](#what-a-call-costs)
- [How the engine measures the machine](#how-the-engine-measures-the-machine)
- [Memory](#memory)
- [Text to speech](#text-to-speech)
- [Overriding the measurements](#overriding-the-measurements)

## What a call costs

The figures below are Synauson's own measurements with the speech to text model the
engine downloads. Treat them as a guide, and check what your own machine reports in
`capabilities().stt`.

- Current Intel server CPUs ran about one real-time STT stream per vCPU. Older Intel
  server CPUs and AMD Genoa ran about half that.
- An 8-core AVX2 desktop CPU (Ryzen 7 3700X) carried about 2 streams.
- VAD and turn detection cost about 0.01 of a CPU core per call.
- The STT turn flush (`sttTurnFlush`) costs about 26% more CPU when on, and lowers the
  stream cap by about a quarter. `sttEager` also sets decoding aside.

Each participant with AI also takes one of the license's AI sessions, and each
conference one of the license's conferences
([Plans and pools](licensing-and-models.md#plans-and-pools)). The license's pools and
the machine's stream cap are separate: a call needs room in all of them.

## How the engine measures the machine

When the STT pool starts, the engine times a decode and measures the memory one
worker's model copy takes, then sets the pool and its stream cap from that.
`jsyn.capabilities().stt` reports the result:

| Field | Meaning |
|---|---|
| `state` | `ready`, `loading`, `idle` or `failed` |
| `workers`, `threadsPerWorker` | The decoding pool |
| `streams` | `limit`, the cap on STT streams, and `inUse` |
| `realTimeFactor` | One stream's decode time over audio time |
| `limitedBy` | What set the number of workers: `cpu`, `memory` or `operator` (`sttCapacity`) |
| `source` | `auto` (measured on this start), `cached` (from an earlier start) or `override` (`sttCapacity`) |
| `turnFlush`, `forecastReserve`, `eager` | What the turn flush and eager end of turn set aside |

The measurement is of one stream alone, so on a shared or busy host it can be
optimistic. When STT can't keep up, the agent stream reports `STT_LAGGING`
([Voice agents](voice-agents.md#problems-reported-on-the-stream)).

The engine sizes itself from the CPU it is allowed: the smallest of the process's
cgroup CPU quota, its cpuset, its affinity mask and the CPU count. A container or a
CPU-limited service therefore sizes its pools to its quota. `capabilities().resources`
reports the CPU budget, where it came from, the CPU model and features, and the memory
limit.

At startup the engine also times one VAD chunk and one turn-detection decision. It
keeps every timing in `calibration.json` in the state directory. A later start reuses
a timing instead of measuring again when the model, the CPU and its features, the CPU
budget, the thread count and the ONNX Runtime version all match; anything else is
measured again. On Linux, a timing taken while the CPU was busy is used for that run
but not cached. `capabilities().calibration` gives the file and the detector timings.
Runtimes that share a state directory share the file, and the last one to write it
wins.

## Memory

STT workers share one copy of the model's weights, about 600 MB, mapped from a file the
engine writes into the model store on its first STT start (about 1.2 GB on disk). Every
process that uses the same store shares it. With a read-only model store each worker
loads a private copy instead, about 800 MB each. `capabilities().stt.sharedModelBytes`
and `modelBytes` report the split. `memoryBudget` (by default the cgroup's limit) caps
how many workers STT starts.

## Text to speech

By default a quarter of the logical CPUs go to TTS: 1 to 4 workers, 2 threads a worker
when there are at least two CPUs for TTS, and 2 utterances a worker. The cap counts
utterances while they are synthesized and played, not speakers.
`capabilities().tts` reports the pool and the cap. `UtteranceDone.underrunMs` shows
whether synthesis kept up: on a machine sized for its load it is 0.

## Overriding the measurements

| `JSynConfig.Builder` | Use it to |
|---|---|
| `sttCapacity(workers, threads, maxStreams)` | Set the STT pool and cap yourself. Each `null` keeps the measured value; setting all three skips the measurement. `maxStreams` 0 turns STT off. |
| `ttsCapacity(workers, threads, maxStreams)` | Set the TTS pool and cap. `maxStreams` 0 turns TTS off. |
| `cpuBudget(cores)` | Share one host between several runtimes without CPU quotas. A value below the detected budget wins; one above it is used and logged as a warning. |
| `memoryBudget(bytes)` | Limit the memory STT sizes itself within |
| `recalibrate(true)` | Measure again after changing hardware in place, or to take fresh timings on an idle host |
