import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
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
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

tasks.named<KotlinJvmCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=11")
}

tasks.named<KotlinJvmCompile>("compileTestKotlin") {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(11)
}

tasks.named<JavaCompile>("compileTestJava") {
    sourceCompatibility = JavaVersion.VERSION_25.toString()
    targetCompatibility = JavaVersion.VERSION_25.toString()
    options.release.set(25)
}

listOf("testCompileClasspath", "testRuntimeClasspath").forEach { configurationName ->
    configurations.named(configurationName) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    api(project(":tokenizer-core"))
    api(project(":tokenizer-format-cbor"))

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
