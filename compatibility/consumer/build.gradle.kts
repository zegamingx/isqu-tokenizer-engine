import java.io.File
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
}

kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.fromVersion("2.4"))
        apiVersion.set(KotlinVersion.fromVersion("2.4"))
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    implementation("io.github.zegamingx:isqu-tokenizer-core:0.1.0-SNAPSHOT")
    implementation("io.github.zegamingx:isqu-tokenizer-training:0.1.0-SNAPSHOT")
    testImplementation("io.github.zegamingx:isqu-tokenizer-testkit:0.1.0-SNAPSHOT")
}

tasks.register("verifyPublishedArtifacts") {
    doLast {
        fun resolvedIsquArtifacts(configurationName: String) =
            configurations.getByName(configurationName)
                .incoming.artifacts.artifacts
                .mapNotNull { artifact ->
                    val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    if (component != null && component.group == "io.github.zegamingx") {
                        component.module to artifact
                    } else {
                        null
                    }
                }
                .toMap()

        val productionArtifacts = resolvedIsquArtifacts("compileClasspath")
        val requiredProductionModules = setOf(
            "isqu-tokenizer-core",
            "isqu-tokenizer-training",
        )
        check(productionArtifacts.keys.containsAll(requiredProductionModules)) {
            "Production classpath is missing ISQU modules: ${requiredProductionModules - productionArtifacts.keys}"
        }
        check("isqu-tokenizer-testkit" !in productionArtifacts) {
            "tokenizer-testkit must not leak onto the production compile classpath"
        }

        val testArtifacts = resolvedIsquArtifacts("testCompileClasspath")
        val requiredTestOnlyModules = setOf(
            "isqu-tokenizer-testkit",
            "isqu-tokenizer-format-cbor",
        )
        check(testArtifacts.keys.containsAll(requiredTestOnlyModules)) {
            "Test classpath is missing ISQU test-only modules: ${requiredTestOnlyModules - testArtifacts.keys}"
        }
        check("isqu-tokenizer-format-cbor" !in productionArtifacts) {
            "The CBOR format adapter must not leak onto the production compile classpath"
        }

        val allResolvedArtifacts = productionArtifacts + testArtifacts
        allResolvedArtifacts.forEach { (moduleName, artifact) ->
            val targetJvm = artifact.variant.attributes.getAttribute(
                TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            )
            check(targetJvm == 11) {
                "Expected $moduleName to publish org.gradle.jvm.version=11, but got $targetJvm"
            }

            check(artifact.file.isFile && artifact.file.extension == "jar") {
                "Resolved artifact for $moduleName is not a JAR: ${artifact.file}"
            }

            val localRepository = File(
                System.getProperty("user.home"),
                ".m2/repository/io/github/zegamingx",
            )
            val versionDirectory = File(localRepository, "$moduleName/0.1.0-SNAPSHOT")
            val baseName = "$moduleName-0.1.0-SNAPSHOT"
            val pomFile = File(versionDirectory, "$baseName.pom")
            val moduleFile = File(versionDirectory, "$baseName.module")
            val sourcesJar = File(versionDirectory, "$baseName-sources.jar")

            check(pomFile.isFile) { "Missing Maven POM for $moduleName" }
            check(moduleFile.isFile) { "Missing Gradle Module Metadata for $moduleName" }
            check(sourcesJar.isFile) { "Missing sources JAR for $moduleName" }

            val pom = pomFile.readText()
            val moduleMetadata = moduleFile.readText()

            check(pom.contains("<name>Apache License, Version 2.0</name>")) {
                "Apache-2.0 license metadata missing from $moduleName POM"
            }
            check(pom.contains("https://github.com/zegamingx/isqu-tokenizer-engine")) {
                "Project SCM/homepage metadata missing from $moduleName POM"
            }
            check(moduleMetadata.contains("\"org.gradle.jvm.version\": 11")) {
                "JVM 11 target metadata missing from $moduleName Gradle Module Metadata"
            }

            when (moduleName) {
                "isqu-tokenizer-training" -> check(
                    pom.contains("<artifactId>isqu-tokenizer-core</artifactId>"),
                ) {
                    "tokenizer-training POM does not expose tokenizer-core as an API dependency"
                }

                "isqu-tokenizer-format-cbor" -> check(
                    pom.contains("<artifactId>isqu-tokenizer-core</artifactId>"),
                ) {
                    "tokenizer-format-cbor POM does not expose tokenizer-core as an API dependency"
                }

                "isqu-tokenizer-testkit" -> {
                    check(pom.contains("<artifactId>isqu-tokenizer-core</artifactId>")) {
                        "tokenizer-testkit POM does not expose tokenizer-core as an API dependency"
                    }
                    check(!pom.contains("<artifactId>isqu-tokenizer-training</artifactId>")) {
                        "tokenizer-testkit must not expose tokenizer-training transitively"
                    }
                }
            }
        }
    }
}
