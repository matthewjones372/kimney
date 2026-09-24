pluginManagement {
    includeBuild("kimney-gradle-plugin")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kimney"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include(
    "kimney-runtime",
    "kimney-derive",
    "kimney-compiler-plugin",
    "example",
    // Builds a consumer twice under incremental compilation (spec 0020).
    "ic-test",
    // A JMH harness, run only when asked for: ./gradlew :benchmarks:jmh
    "benchmarks",
)
