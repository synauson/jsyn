
allprojects {
    group = "com.synauson"
    version = (System.getenv("JSYN_VERSION") ?: "0.1.0-SNAPSHOT").removePrefix("v")
    repositories {
        mavenCentral()
        // Native artifacts (jsyn-natives-linux, jsyn-natives-windows) are pre-built
        // binaries published from the synauson build system. Nexus credentials are
        // required to resolve them. Set NEXUS_USER and NEXUS_PASSWORD in the environment
        // or in ~/.gradle/gradle.properties.
        maven {
            name = "Nexus"
            url = uri(System.getenv("NEXUS_URL") ?: "https://nexus.benashby.com/repository/maven-public/")
            credentials {
                username = System.getenv("NEXUS_USER") ?: ""
                password = System.getenv("NEXUS_PASSWORD") ?: ""
            }
        }
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
    // Nexus round-trip) so "SNAPSHOT" actually means current.
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
        repositories {
            maven {
                name = "Nexus"
                val nexusBaseUrl = System.getenv("NEXUS_URL") ?: "https://nexus.benashby.com"
                val repoName = if (version.toString().endsWith("-SNAPSHOT")) {
                    "maven-snapshots"
                } else {
                    "maven-releases"
                }
                url = uri("$nexusBaseUrl/repository/$repoName")
                credentials {
                    username = System.getenv("NEXUS_USER")
                    password = System.getenv("NEXUS_PASSWORD")
                }
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
