# jsyn CI integration testing — design

Status: approved, not yet implemented.

## Context

`jsyn` is the Java client library for `synauson`, consumed by embedding
`jsyn-natives-linux`/`jsyn-natives-windows` (published by the `synauson`
repo to Nexus) and calling into the JNI boundary they contain. The
repository has 15 real integration tests
(`jsyn/src/test/java/com/synauson/jsyn/it/*IT.java`) that exercise that
boundary — `NativeLoader.load()`, `System.load()` of the native library,
`Java_*` calls into conference/participant/detector lifecycle, and (for
three of them) real ONNX model inference through the native path.

None of these tests have ever run automatically. `jsyn`'s
`.github/workflows/ci.yml` has exactly two jobs: `java-compile` (runs
`:jsyn:compileJava :jsyn:compileTestJava` — compiles the test *source*,
never executes it) and `publish-snapshot` (publishes
`com.synauson:jsyn:1.0.0-SNAPSHOT` to Nexus on every push to `main`,
gated only on `java-compile` succeeding). A manual audit run on
2026-08-05 (this repo's dev machine, NixOS) surfaced two things:

1. Two environment gaps (missing `LD_LIBRARY_PATH` entries, then a glibc
   version mismatch) that are specific to that machine and don't apply to
   GitHub-hosted runners — inconclusive, not a defect.
2. A real, reproducible bug: `VadDetectorIT`, `SmartTurnDetectorIT`, and
   `RealVadE2eLatencyIT` each hardcode a relative path
   (`../../../synauson-server/tests/fixtures/short_speech.wav`,
   `../../../models`) to reach fixtures and ONNX models that live in the
   sibling `synauson` repo. The path has one `../` too many and is
   missing a `synauson/` path segment — it resolves outside any
   checked-out repo in the standard sibling layout
   (`~/projects/synauson/{jsyn,synauson}`) documented in both repos. All
   three tests silently `Assumptions.assumeTrue`-skip rather than fail,
   so this has gone unnoticed since they were written.

The net effect: nothing has ever verified that `jsyn` actually works —
not in CI, not manually, on any platform — despite the test suite
existing and being reasonably well-designed (e.g. `forkEvery = 1` on
Windows to isolate GStreamer/ORT's process-global state per test class).

## Goal

Wire the existing IT suite into GitHub Actions on both Linux and Windows,
using GitHub-hosted runners (matching the `synauson` repo's own CI,
migrated 2026-08-05), and fix the path bug that's been silently
suppressing the three model-touching tests. Gate the Nexus snapshot
publish on tests actually passing.

Explicitly not in scope: `jsyn`'s `release.yml` (tag-triggered releases),
self-hosted runners of any kind, and any change to the `synauson` repo's
own CI or artifacts.

## Design

### Job structure

Two new jobs in `jsyn/.github/workflows/ci.yml`, alongside the existing
`java-compile`:

```yaml
test-linux:
  runs-on: ubuntu-24.04
  needs: java-compile
  timeout-minutes: 30
test-windows:
  runs-on: windows-2022
  needs: java-compile
  timeout-minutes: 45
```

Each job:

1. `actions/checkout@v4` for `jsyn` (default path).
2. `actions/checkout@v4` with `repository: synauson/synauson`,
   `path: synauson-repo` — a second checkout of the sibling repo into a
   known-relative path, purely to expose `models/*.onnx` and
   `synauson-server/tests/fixtures/short_speech.wav` to the test JVM. No
   Rust build, no protoc, no GStreamer *development* packages — `jsyn`
   only needs the pre-built native library plus a matching runtime.
3. Runtime install, deliberately minimal versus `synauson`'s own CI
   (confirmed: don't mirror its Rust-toolchain-oriented setup):
   - Linux: `libgstreamer1.0-0`, `gstreamer1.0-plugins-{base,good,bad,ugly}`,
     `gstreamer1.0-libav`, `gstreamer1.0-nice` (runtime packages, no
     `-dev` — matches the `synauson-server` Docker image's runtime stage,
     not its builder stage), plus the ORT `.so` extracted the same way
     `synauson`'s workflows already do.
   - Windows: the GStreamer runtime MSI only (not the devel MSI — no
     pkgconf/protoc needed since nothing compiles Rust here), plus the
     ORT `.zip` extraction, reusing the same `actions/cache` key pattern
     `synauson`'s `ci.yml` already uses for the MSI/zip downloads.
4. `actions/setup-java@v4`, Temurin 11 (matches `jsyn`'s existing
   `java-compile` job and the `JavaLanguageVersion.of(11)` toolchain
   pinned in `build.gradle.kts`).
5. `./gradlew :jsyn:test --no-daemon --console=plain
   -DsynausonRepoDir=<absolute path to the synauson-repo checkout>`.

#### Cross-repo checkout authentication

`synauson` is a private repository. The default `GITHUB_TOKEN` a workflow
run receives is scoped to the repository the workflow lives in
(`jsyn`) — it cannot check out a different private repo, even one in the
same org. Step 2 above therefore needs an explicit `token:` on that
`actions/checkout` call, backed by a PAT with `repo` read scope on
`synauson/synauson`, stored as a new repository secret (e.g.
`SYNAUSON_REPO_TOKEN`) on `jsyn`. This is the one new secret this design
requires; everything else reuses `jsyn`'s existing Nexus secrets
untouched.

`publish-snapshot` gains `needs: [test-linux, test-windows]`. This is the
one behavior change with product impact: today, any commit that merely
compiles publishes a new `1.0.0-SNAPSHOT` to Nexus regardless of whether
the native boundary actually works. After this change, a real regression
in the JNI/native/model path blocks the publish instead of shipping
silently — closing exactly the gap this design exists to close.

### Fixing the path bug

Rather than patch the same broken relative path in three places, add one
shared helper,
`jsyn/src/test/java/com/synauson/jsyn/it/support/SynausonRepoLocator.java`:

```java
final class SynausonRepoLocator {
    private SynausonRepoLocator() {}

    /** Resolves the synauson repo checkout used for model/fixture files.
     * CI passes -DsynausonRepoDir explicitly; local runs fall back to the
     * standard sibling-repo layout (~/projects/synauson/{jsyn,synauson}). */
    static Path resolve() {
        String override = System.getProperty("synausonRepoDir");
        if (override != null) {
            return Paths.get(override).toAbsolutePath();
        }
        // From jsyn/jsyn/ (the Gradle project dir), climb to the shared
        // parent and back down into the sibling synauson checkout.
        return Paths.get("../../synauson").toAbsolutePath();
    }
}
```

`VadDetectorIT`, `SmartTurnDetectorIT`, and `RealVadE2eLatencyIT` change
their two `Paths.get("../../../...")` calls to
`SynausonRepoLocator.resolve().resolve("synauson-server/tests/fixtures/short_speech.wav")`
and `.resolve("models")` respectively. The `Assumptions.assumeTrue`
skip-if-missing behavior stays as a safety net for anyone who genuinely
doesn't have `synauson` checked out locally — it just won't misfire
against a phantom path anymore.

This also fixes the bug for `just java-test` run locally in the
documented sibling layout, with no configuration required — the fallback
path is correct where the old one wasn't.

### Stress test

`NativeParticipantStressIT` (`sustainedTrafficFor10Seconds`) is excluded
from `test-linux`/`test-windows` via a JUnit `@Tag("stress")` +
`excludeTags("stress")` in the `tasks.test` block, matching the rationale
`synauson`'s own `stress-tests.yml` already states for its manual-only
workflow: timing-sensitive tests produce false negatives on shared
runners. It is not wired into any automated job in this design — running
it remains a manual `./gradlew :jsyn:test --tests
'*NativeParticipantStressIT*'` invocation. A future `jsyn`
`stress-tests.yml` mirroring `synauson`'s can be added later if desired;
out of scope here.

### Triggers

Unchanged from `jsyn`'s existing `ci.yml`: `push` to `main`, and
`pull_request`. The new jobs run on both, same as `java-compile`.

## Cost

Using `synauson`'s measured 2026-08-05 numbers as a baseline (its
Windows job, which also compiles Rust, took 14–20 min per run) and
`jsyn`'s narrower scope (no Rust compile, no protoc), the two new jobs
are estimated at roughly 8–12 min (Linux) + 12–18 min (Windows) per
trigger — approximately 50 GitHub Actions allowance-minutes per push
(Windows counted at its 2x multiplier), on top of `synauson`'s own
~118/push. Both comfortably inside GitHub Team's 3,000 included
minutes/month at this project's push cadence.

## Testing

This design is self-verifying: the first real CI run of `test-linux` and
`test-windows` either passes (proving the artifacts work end-to-end for
the first time) or fails with a concrete, actionable error — either
outcome is strictly better than the current silent-skip/never-run state.
No separate test plan is needed beyond "iterate until both jobs are
green," per the implementation plan.
