// A build of its own rather than a module, because a plugin has to be built
// before the build applying it is configured. The root build includes it, so
// `example` applies `io.github.matthewjones372.kimney` by id, as a user does.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kimney-gradle-plugin"

dependencyResolutionManagement {
    repositories { mavenCentral() }
    // The root build's catalog, so both builds pin the same Kotlin.
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
