plugins {
    application
    id("io.github.matthewjones372.kimney")
}

application {
    mainClass.set("example.MainKt")
}

// A user's build resolves the artifacts the plugin adds from Central. Inside
// this build they are projects, and Gradle does not match a project to its
// coordinates by itself.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.matthewjones372:kimney-compiler-plugin")).using(project(":kimney-compiler-plugin"))
        substitute(module("io.github.matthewjones372:kimney-runtime")).using(project(":kimney-runtime"))
    }
}
