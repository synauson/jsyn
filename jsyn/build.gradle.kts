dependencies {
    api("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Native artifacts are pre-built platform JARs published separately by the
    // synauson build system. NativeLoader picks the right .so/.dll at JVM startup
    // from os.name / os.arch, so both can be on the classpath simultaneously.
    //
    // nativesVersion is pinned independently of jsyn's own version so jsyn can
    // advance without requiring a new synauson/natives release. Only bump this
    // when jsyn adds JNI calls that require a newer compiled native.
    val nativesVersion = findProperty("jsynNativesVersion") as String? ?: "1.0.0-SNAPSHOT"
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("windows")) {
        testRuntimeOnly("com.synauson:jsyn-natives-windows:${nativesVersion}")
    } else {
        testRuntimeOnly("com.synauson:jsyn-natives-linux:${nativesVersion}")
    }
}

tasks.test {
    useJUnitPlatform()

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
