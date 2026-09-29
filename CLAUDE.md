# jsyn

jsyn is the public Java SDK for the Synauson engine. It calls into `synauson-jni` over JNI.
The engine lives in the private repository https://github.com/synauson/synauson; complete
example applications live in https://github.com/synauson/examples. `README.md` is the user
documentation. Read it rather than repeating it here.

## Commands

| Command | Needs |
|---|---|
| `./gradlew :jsyn:compileJava :jsyn:compileTestJava :jsyn:checkReadmeSnippets` | A JDK only. This is CI's `java-compile` job. |
| `./gradlew :jsyn:test --tests '*Test'` | Nothing native. Pure-Java unit tests; the natives jar is downloaded but never loaded. |
| `./gradlew :jsyn:test -DsynausonRepoDir=<synauson checkout>` | GStreamer, `SYNAUSON_LICENSE_KEY`, network access, and Playwright Chromium (`./gradlew :jsyn:installPlaywrightBrowsers`). Runs every `*IT`. |
| `./gradlew :jsyn:test -PrunStress --tests '*NativeParticipantStressIT*'` | As above. The `stress` tag is excluded unless `-PrunStress` is set. |
| `./gradlew :jsyn:javadoc` | A JDK. CI builds javadoc only when publishing, so run this after touching public javadoc. |

Without `-DsynausonRepoDir`, the tests look for a synauson checkout next to this one
(`../../synauson` from `jsyn/`). They import `models/*.onnx` from it into
`jsyn/build/model-store` and fail if that import fails. Tests that need
`synauson-server/tests/fixtures/short_speech.wav` skip without it.

`-PjsynNativesVersion=<v>` overrides the natives version for one run.

## Writing tests

- New ITs get their runtime from `JSynTestHelpers.newJSyn()`, which gives each instance its
  own RTP port range and the shared model store. On Linux every test class shares one JVM
  (`forkEvery = 1` is Windows only), so a test must restore any system property it changes
  and must not assume fixed ports.
- A test for engine behaviour newer than some natives should `assumeTrue` the feature is
  present. `WebRtcOptionsE2eIT` shows the pattern.
- A new capability needs a jsyn test that exercises it, and a row in the README's "Use
  cases" table linking that test. Renaming or deleting an IT means updating the table.

## Natives and the engine

Tests take the natives as `testRuntimeOnly com.synauson:jsyn-natives-{linux,windows}` at
`jsynNativesVersion` from `gradle.properties`. They resolve from
`https://maven.synauson.com/releases` and `/snapshots`, and snapshots are rechecked on every
build.

Unreleased engine changes are tested from the engine side. The synauson repository's CI
jobs `java-linux` and `java-windows` build the natives as `0.0.0-ci-SNAPSHOT`, publish them
to `mavenLocal`, check out the jsyn branch with the same name as the engine branch (else
`main`), and run `:jsyn:test` against them. A change that spans both repositories
therefore uses same-named branches in both. jsyn's own CI keeps using released natives,
so it fails on a new native call until the natives are released.

A new native call needs a natives release from synauson, then a bump of
`jsynNativesVersion` in `gradle.properties` with a paragraph there saying what that
version adds. The fallback default in `jsyn/build.gradle.kts` must match.

jsyn and the natives drift: an engine-only fix ships as a natives release with no jsyn
release. After every natives release, bump `jsynNativesVersion` (with its paragraph)
and the README's natives version in the install snippets and under "Versions", so CI
tests the newest natives and the docs recommend them.

## The JNI contract

- Every `static native` method in `internal/NativeBridge.java` has a matching
  `Java_com_synauson_jsyn_internal_NativeBridge_<name>` export in synauson's
  `synauson-jni/src/exports/*.rs`. Change both sides together.
- `synauson-jni/src/jni_cache.rs` looks up classes by fully qualified name and constructor
  signature: every `exception/*Exception`, `EventStreamObserver`, the event classes
  (`VadEvent$SpeechStart`, `VadEvent$SpeechEnd`, `SmartTurnEvent$TurnResult`, the
  `FileEvent$*` subtypes, `DtmfEvent`, `IceCandidateEvent`) and
  `internal/NativeParticipantNativeHandle`. Renaming, moving, or changing a constructor
  compiles fine and then fails at run time: the exception classes are looked up when
  `new JSyn` starts the runtime, and the event classes when a stream is subscribed.
  The JNI signatures are written in each class's javadoc; keep them accurate.
- `JSynConfig` fields serialize camelCase and must match the Rust `ConfigJson`. Spec
  classes serialize snake_case through `@SerializedName`. Gson omits null fields, so an
  unset option reaches the engine as absent and takes the engine default. Keep it that
  way; `WebRtcParticipantSpecJsonTest` pins the shape.

## README snippets

The README's quickstart is a copy of the region between `// snippet: quickstart` and
`// end snippet: quickstart` in `jsyn/src/test/java/com/synauson/jsyn/docs/Quickstart.java`.
`compileTestJava` compiles that file, and `:jsyn:checkReadmeSnippets` (part of `check` and
of CI's `java-compile`) fails if the README block differs. Edit the Java file, then copy
the region into the README. For another snippet, add a region to a file in that `docs`
package and put `<!-- snippet: <name> -->` on the line before the README's code fence.

## Versions and releases

- The root `build.gradle.kts` sets the version: `JSYN_VERSION` if set (release tags),
  else a hard-coded `-SNAPSHOT` of the next minor after the latest tag. CI's `publish-snapshot` job publishes that
  snapshot on every push to `main` once all test jobs pass.
- A `v<x.y.z>` tag runs `release.yml`, which publishes that version and runs no tests.
  Tag only a commit that is already on `main`.
- After a release, bump the snapshot version in `build.gradle.kts` to the next minor, and
  update the README's install snippets (Gradle and Maven) and the pairing sentence under
  "Versions".
- Publishing needs the `MAVEN_*` secrets. Without them the publish task does not exist,
  so it cannot be run locally.

## Public repository rules

IMPORTANT: this repository is public. Code, comments, docs and commit messages must not
contain personal names, email addresses, home directory paths, private hostnames or
credentials.

- Commits land only as `synauson[bot]`, through the maintainer's `synauson-bot` tool. Do
  not run `git commit` or `git push` yourself; leave changes in the working tree for the
  maintainer.
- A commit is a subject line only: lowercase, imperative, under 60 characters, no trailing
  period. No body, no trailers, no AI attribution.
- Pull requests are disabled. The bot commits to a branch, CI runs on every branch push,
  and `main` only fast-forwards to a commit whose required checks passed.
