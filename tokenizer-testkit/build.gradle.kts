plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        allWarningsAsErrors.set(true)
    }
}

java {
    withSourcesJar()
}

dependencies {
    api(project(":tokenizer-core"))
    api(project(":tokenizer-training"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}
