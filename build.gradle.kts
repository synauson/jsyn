
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
