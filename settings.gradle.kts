pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "isqu-tokenizer-engine"

include(
    ":tokenizer-core",
    ":tokenizer-format-cbor",
    ":tokenizer-training",
    ":tokenizer-testkit",
)
