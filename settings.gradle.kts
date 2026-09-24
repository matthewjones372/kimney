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
    // A JMH harness, run only when asked for: ./gradlew :benchmarks:jmh
    "benchmarks",
)
