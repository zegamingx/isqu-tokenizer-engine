import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.zegamingx.isqu.compatibility"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zegamingx.isqu.compatibility"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation("io.github.zegamingx:isqu-tokenizer-core:0.1.0-SNAPSHOT")
    implementation("io.github.zegamingx:isqu-tokenizer-training:0.1.0-SNAPSHOT")
    testImplementation("io.github.zegamingx:isqu-tokenizer-testkit:0.1.0-SNAPSHOT")
}

tasks.register("verifyAndroidArtifacts") {
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

        val productionArtifacts = resolvedIsquArtifacts("debugCompileClasspath")
        val requiredProductionModules = setOf(
            "isqu-tokenizer-core",
            "isqu-tokenizer-training",
        )
        check(productionArtifacts.keys.containsAll(requiredProductionModules)) {
            "Android production classpath is missing ISQU modules: ${requiredProductionModules - productionArtifacts.keys}"
        }
        check("isqu-tokenizer-testkit" !in productionArtifacts) {
            "tokenizer-testkit must not leak onto the Android production classpath"
        }

        val unitTestArtifacts = resolvedIsquArtifacts("testDebugUnitTestCompileClasspath")
        check("isqu-tokenizer-testkit" in unitTestArtifacts) {
            "tokenizer-testkit must be available to Android unit tests"
        }

        (productionArtifacts + unitTestArtifacts).forEach { (moduleName, artifact) ->
            val targetJvm = artifact.variant.attributes.getAttribute(
                TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            )
            check(targetJvm == 11) {
                "Expected $moduleName to publish org.gradle.jvm.version=11, but got $targetJvm"
            }
        }
    }
}
