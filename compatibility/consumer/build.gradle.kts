import java.io.File
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.fromVersion("2.4"))
        apiVersion.set(KotlinVersion.fromVersion("2.4"))
        jvmTarget.set(JvmTarget.JVM_11)
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

dependencies {
    implementation("io.github.zegamingx:isqu-tokenizer-core:0.1.0-SNAPSHOT")
    implementation("io.github.zegamingx:isqu-tokenizer-training:0.1.0-SNAPSHOT")
    implementation("io.github.zegamingx:isqu-tokenizer-testkit:0.1.0-SNAPSHOT")
}

tasks.register("verifyPublishedArtifacts") {
    doLast {
        val artifacts = configurations
            .getByName("compileClasspath")
            .incoming
            .artifacts
            .artifacts

        val isquArtifacts = artifacts.filter { artifact ->
            val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier
            component != null && component.group == "io.github.zegamingx"
        }

        val artifactsByModule = isquArtifacts.associateBy { artifact ->
            val component = artifact.id.componentIdentifier as ModuleComponentIdentifier
            component.module
        }

        val requiredModules = setOf(
            "isqu-tokenizer-core",
            "isqu-tokenizer-training",
            "isqu-tokenizer-testkit",
        )
        val missingModules = requiredModules - artifactsByModule.keys

        check(missingModules.isEmpty()) {
            "Missing published ISQU modules from compileClasspath: $missingModules"
        }

        val localRepository = File(
            System.getProperty("user.home"),
            ".m2/repository/io/github/zegamingx",
        )

        artifactsByModule.forEach { (moduleName, artifact) ->
            val targetJvm = artifact.variant.attributes.getAttribute(
                TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            )
            check(targetJvm == 8) {
                "Expected $moduleName to publish org.gradle.jvm.version=8, but got $targetJvm"
            }

            check(artifact.file.isFile && artifact.file.extension == "jar") {
                "Resolved artifact for $moduleName is not a JAR: ${artifact.file}"
            }

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
            check(moduleMetadata.contains("\"org.gradle.jvm.version\": 8")) {
                "JVM 8 target metadata missing from $moduleName Gradle Module Metadata"
            }

            when (moduleName) {
                "isqu-tokenizer-training" -> check(
                    pom.contains("<artifactId>isqu-tokenizer-core</artifactId>"),
                ) {
                    "tokenizer-training POM does not expose tokenizer-core as an API dependency"
                }

                "isqu-tokenizer-testkit" -> {
                    check(pom.contains("<artifactId>isqu-tokenizer-core</artifactId>")) {
                        "tokenizer-testkit POM does not expose tokenizer-core as an API dependency"
                    }
                    check(pom.contains("<artifactId>isqu-tokenizer-training</artifactId>")) {
                        "tokenizer-testkit POM does not expose tokenizer-training as an API dependency"
                    }
                }
            }
        }
    }
}
