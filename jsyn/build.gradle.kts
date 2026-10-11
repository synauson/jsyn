dependencies {
    api("com.google.code.gson:gson:2.11.0")
    // JSpecify nullness annotations (@NullMarked, @Nullable) on the public API.
    // Annotation-only, no runtime behaviour. `api` rather than compileOnly: the
    // annotations appear in public signatures, and JSpecify asks that consumers
    // get them transitively so NullAway, Kotlin and IDEs can read them.
    api("org.jspecify:jspecify:1.0.0")
}

// The test build, applied where present. Releases don't include it.
rootProject.file("gradle/dev-tests.gradle.kts").takeIf { it.exists() }?.let { apply(from = it) }
