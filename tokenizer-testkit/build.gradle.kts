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

// Production Kotlin code is limited to the Java 11 API surface used by Android API 33.
// -Xjdk-release also pins the production class-file version to Java 11.
tasks.named<KotlinJvmCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=11")
}

// Tests run on JDK 25 and may use the modern JUnit 6 toolchain independently of
// the production artifact's Java 11 compatibility floor.
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

// JUnit 6 requires Java 17+ at runtime. Resolve test dependencies against the
// JDK 25 test target instead of inheriting the Java 11 production target.
listOf("testCompileClasspath", "testRuntimeClasspath").forEach { configurationName ->
    configurations.named(configurationName) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    api(project(":tokenizer-core"))

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
