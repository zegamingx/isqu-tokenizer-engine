import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "io.github.zegamingx"
    version = providers.gradleProperty("releaseVersion")
        .orElse("0.1.0-SNAPSHOT")
        .get()
}

subprojects {
    val moduleProject = this

    moduleProject.plugins.withId("java-library") {
        moduleProject.pluginManager.apply("maven-publish")

        moduleProject.extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifactId = "isqu-${moduleProject.name}"
                    from(moduleProject.components.getByName("java"))

                    pom {
                        name.set("ISQU Tokenizer Engine - ${moduleProject.name}")
                        description.set(
                            "A module of ISQU Tokenizer Engine, a deterministic and portable Kotlin/JVM tokenizer library."
                        )
                        url.set("https://github.com/zegamingx/isqu-tokenizer-engine")

                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }

                        developers {
                            developer {
                                id.set("zegamingx")
                            }
                        }

                        scm {
                            connection.set("scm:git:https://github.com/zegamingx/isqu-tokenizer-engine.git")
                            developerConnection.set("scm:git:ssh://git@github.com/zegamingx/isqu-tokenizer-engine.git")
                            url.set("https://github.com/zegamingx/isqu-tokenizer-engine")
                        }
                    }
                }
            }
        }
    }
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.9.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228"
    networkTimeout = 60_000
    validateDistributionUrl = true
}
