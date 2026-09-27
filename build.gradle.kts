
allprojects {
    group = "com.synauson"
    version = (System.getenv("JSYN_VERSION") ?: "0.1.0-SNAPSHOT").removePrefix("v")
    repositories {
        mavenCentral()
        // jsyn-natives-linux and jsyn-natives-windows are published to the public
        // Synauson Maven repository; no credentials are needed to resolve them.
        maven { url = uri("https://maven.synauson.com/releases") }
        maven { url = uri("https://maven.synauson.com/snapshots") }
    }

    // jsyn-natives-* is consumed as 1.0.0-SNAPSHOT, a *changing* module: the
    // coordinates stay fixed while synauson republishes new content behind
    // them. Gradle caches changing modules for 24 hours by default, and CI
    // restores ~/.gradle/caches across runs via actions/cache restore-keys —
    // so a cache seeded by an earlier run keeps serving that run's native to
    // every later run inside the TTL, silently testing a stale binary. That
    // is not hypothetical: run 31062486992 tested build 3 of the native and
    // failed three SIP tests against synauson bugs already fixed in build 8.
    // A zero TTL makes every build re-check maven-metadata.xml (one cheap
    // repository round-trip) so "SNAPSHOT" actually means current.
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(11) }
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("library") {
                from(components.findByName("java"))
            }
        }
        // CI publishes with MAVEN_RELEASES_URL or MAVEN_SNAPSHOTS_URL (chosen by the
        // version) plus MAVEN_USER and MAVEN_PASSWORD. Without the URL the "synauson"
        // repository doesn't exist, so the publish task named after it fails fast.
        val snapshot = version.toString().endsWith("-SNAPSHOT")
        System.getenv(if (snapshot) "MAVEN_SNAPSHOTS_URL" else "MAVEN_RELEASES_URL")?.let { target ->
            repositories {
                maven {
                    name = "synauson"
                    url = uri(target)
                    credentials {
                        username = System.getenv("MAVEN_USER")
                        password = System.getenv("MAVEN_PASSWORD")
                    }
                }
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
