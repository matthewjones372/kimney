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

// The pages DocsMatchTheirFilesTest holds to the recipes they quote, declared as inputs so an edit to either side
// reruns it rather than hitting the cache.
tasks.named<Test>("test") {
    inputs.files(rootProject.file("README.md"), rootProject.file("docs/cookbook.md"), rootProject.file("docs/why.md"))
        .withPropertyName("quotingPages")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    val repoRoot = rootDir.path
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dkimney.repoRoot=$repoRoot") })
}
