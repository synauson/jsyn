
allprojects {
    group = "com.synauson"
    // A v* tag sets JSYN_VERSION (release.yml). Otherwise this is the snapshot
    // main publishes: the minor after the latest tag. Bump it after each release.
    version = (System.getenv("JSYN_VERSION") ?: "1.6.0-SNAPSHOT").removePrefix("v")
    repositories {
        mavenCentral()
        // jsyn-natives-linux and jsyn-natives-windows are published to the public
        // Synauson Maven repository; no credentials are needed to resolve them.
        maven { url = uri("https://maven.synauson.com/releases") }
        maven { url = uri("https://maven.synauson.com/snapshots") }
    }

    // When jsynNativesVersion is a -SNAPSHOT it is a *changing* module: the
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
        // CI publishes to the R2 bucket behind https://maven.synauson.com
        // (releases/ or snapshots/, chosen by the version) with Gradle's S3
        // transport: R2_PUBLISH_ACCESS_KEY_ID and R2_PUBLISH_SECRET_ACCESS_KEY,
        // plus -Dorg.gradle.s3.endpoint=$R2_PUBLISH_ENDPOINT on the command line.
        // Without the key the "synauson" repository doesn't exist, so the publish
        // task named after it fails fast.
        val prefix = if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"
        System.getenv("R2_PUBLISH_ACCESS_KEY_ID")?.let { keyId ->
            repositories {
                maven {
                    name = "synauson"
                    url = uri("s3://synauson-maven/$prefix")
                    credentials(AwsCredentials::class) {
                        accessKey = keyId
                        secretKey = System.getenv("R2_PUBLISH_SECRET_ACCESS_KEY")
                    }
                }
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
