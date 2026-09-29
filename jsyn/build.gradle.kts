dependencies {
    api("com.google.code.gson:gson:2.11.0")
    // JSpecify nullness annotations (@NullMarked, @Nullable) on the public API.
    // Annotation-only, no runtime behaviour. `api` rather than compileOnly: the
    // annotations appear in public signatures, and JSpecify asks that consumers
    // get them transitively so NullAway, Kotlin and IDEs can read them.
    api("org.jspecify:jspecify:1.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.microsoft.playwright:playwright:1.47.0")
    // Native artifacts are pre-built platform JARs published separately by the
    // synauson build system. NativeLoader picks the right .so/.dll at JVM startup
    // from os.name / os.arch, so both can be on the classpath simultaneously.
    //
    // nativesVersion is pinned independently of jsyn's own version so jsyn can
    // advance without requiring a new synauson/natives release. Only bump this
    // when jsyn adds JNI calls that require a newer compiled native.
    val nativesVersion = findProperty("jsynNativesVersion") as String? ?: "1.5.0"
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("windows")) {
        testRuntimeOnly("com.synauson:jsyn-natives-windows:${nativesVersion}")
    } else {
        testRuntimeOnly("com.synauson:jsyn-natives-linux:${nativesVersion}")
    }
}

tasks.test {
    // Gradle's Test task runs in a forked JVM and does not automatically
    // forward -D system properties given to `./gradlew` on the command
    // line into that forked worker. CI passes -DsynausonRepoDir=<path>
    // (see .github/workflows/ci.yml); without this explicit forward,
    // JSynTestHelpers.resolveSynausonRepo() would read null inside the
    // actual test JVM and silently fall back to the local-dev relative
    // path, which doesn't exist under the CI checkout layout.
    System.getProperty("synausonRepoDir")?.let { systemProperty("synausonRepoDir", it) }

    useJUnitPlatform {
        // NativeParticipantStressIT (10s sustained traffic) is excluded from
        // the always-on suite by default — timing-sensitive tests produce
        // false negatives on busy shared runners (same rationale as
        // synauson's own manual-only stress-tests.yml). Run it explicitly
        // with: ./gradlew :jsyn:test -PrunStress --tests '*NativeParticipantStressIT*'
        // (Gradle ANDs excludeTags with --tests, so the tag must be let
        // through via -PrunStress before --tests can select the class.)
        if (!project.hasProperty("runStress")) {
            excludeTags("stress")
        }
    }

    // Isolate each test class in its own JVM process on Windows. GStreamer and
    // ONNX Runtime are process-global singletons with slow resource cleanup;
    // forkEvery=1 ensures each test class gets a fresh JVM + GStreamer runtime,
    // then tears down completely before the next test starts. Prevents "no
    // buffer reached tee within 10s" timeouts caused by cross-test resource
    // contention. Matches the Rust test serialization strategy (--test-threads=1).
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        forkEvery = 1  // Restart JVM after every 1 test class
        maxParallelForks = 1  // Only one JVM at a time
    }
}

tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "verification"
    description = "Downloads the Chromium build Playwright drives for WebRtcMediaE2eIT. " +
        "Set INSTALL_PLAYWRIGHT_DEPS=1 to also install Linux OS-level dependencies (CI only)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args = if (System.getenv("INSTALL_PLAYWRIGHT_DEPS") == "1") {
        listOf("install", "chromium", "--with-deps")
    } else {
        listOf("install", "chromium")
    }
}

// README code blocks are copies of regions in test sources that compileTestJava
// builds, so the README cannot show code that no longer compiles. A block is
// "<!-- snippet: NAME -->" followed by a fenced block; its source is the lines
// between "// snippet: NAME" and "// end snippet: NAME" in src/test/java/**/docs/.
// Every region must appear in the README, and every README marker must have a region.
val checkReadmeSnippets by tasks.registering {
    group = "verification"
    description = "Fails when a README code block differs from its source region in the test sources."
    val readme = rootProject.file("README.md")
    val sources = fileTree("src/test/java") { include("**/docs/*.java") }
    inputs.file(readme)
    inputs.files(sources)
    doLast {
        val regions = linkedMapOf<String, List<String>>()
        val start = Regex("""^\s*// snippet: (\S+)\s*$""")
        for (file in sources.files.sorted()) {
            val lines = file.readLines()
            lines.forEachIndexed { i, line ->
                val name = start.find(line)?.groupValues?.get(1) ?: return@forEachIndexed
                val end = lines.drop(i + 1).indexOfFirst { it.trim() == "// end snippet: $name" }
                if (end < 0) throw GradleException("${file.name}: snippet '$name' has no '// end snippet: $name'")
                val body = lines.subList(i + 1, i + 1 + end)
                val indent = body.filter { it.isNotBlank() }.minOfOrNull { l -> l.takeWhile { it == ' ' }.length } ?: 0
                regions[name] = body.map { it.drop(indent).trimEnd() }
            }
        }
        val marker = Regex("""^<!-- snippet: (\S+) -->\s*$""")
        val readmeLines = readme.readLines()
        val shown = mutableSetOf<String>()
        val problems = mutableListOf<String>()
        readmeLines.forEachIndexed { i, line ->
            val name = marker.find(line)?.groupValues?.get(1) ?: return@forEachIndexed
            shown += name
            val expected = regions[name]
            if (expected == null) {
                problems += "README.md:${i + 1}: no '// snippet: $name' region under src/test/java/**/docs/"
                return@forEachIndexed
            }
            val open = i + 1
            if (open >= readmeLines.size || !readmeLines[open].startsWith("```")) {
                problems += "README.md:${i + 1}: snippet '$name' marker must be followed by a ``` fence"
                return@forEachIndexed
            }
            val close = readmeLines.drop(open + 1).indexOfFirst { it.startsWith("```") }
            val actual = if (close < 0) readmeLines.drop(open + 1) else readmeLines.subList(open + 1, open + 1 + close)
            val a = actual.map { it.trimEnd() }
            if (a != expected) {
                val at = a.indices.firstOrNull { it >= expected.size || a[it] != expected[it] } ?: a.size
                problems += "README.md:${open + 2 + at}: snippet '$name' differs from its source region" +
                    " (README: '${a.getOrNull(at) ?: "<end>"}', source: '${expected.getOrNull(at) ?: "<end>"}')"
            }
        }
        (regions.keys - shown).forEach { problems += "snippet '$it' is not shown in README.md (add '<!-- snippet: $it -->')" }
        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString("\n") +
                "\nCopy the region from the test source into README.md; the source is the one that compiles.")
        }
    }
}

tasks.named("check") { dependsOn(checkReadmeSnippets) }
