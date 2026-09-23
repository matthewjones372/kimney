// Loaded by the compiler, which runs on 17 at the oldest.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java { targetCompatibility = JavaVersion.VERSION_17 }

// The sources FunctionalStyleTest judges. Declared as task inputs as well as
// handed over, so an edit to any of them reruns the test instead of hitting
// the cache. A new module with main sources gets a line here.
val styledSources = listOf("kimney-runtime", "kimney-derive", "kimney-compiler-plugin")
    .map { rootDir.resolve("$it/src/main/kotlin") }

tasks.named<Test>("test") {
    inputs.files(styledSources)
        .withPropertyName("functionalStyleSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    val repoRootPath = rootDir.path
    val styledSourcePaths = styledSources.joinToString(File.pathSeparator) { it.path }
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Dkimney.style.repoRoot=$repoRootPath", "-Dkimney.style.sources=$styledSourcePaths")
        },
    )
}
