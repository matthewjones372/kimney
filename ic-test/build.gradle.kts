// Builds a two-module consumer, changes a type a mapping reads, and builds it again: the only test that runs
// Kotlin's incremental compilation, which a compiler test does not (spec 0020).

dependencies {
    testImplementation(gradleTestKit())
}

// The Kotlin the nested builds apply: the catalog's, or `-Pkimney.kotlinUnderTest`, as for the compiler tests.
val kotlinUnderTest: String = providers.gradleProperty("kimney.kotlinUnderTest").getOrElse(libs.versions.kotlin.get())

tasks.named<Test>("test") {
    dependsOn(
        ":kimney-runtime:publishAllPublicationsToIcTestRepository",
        ":kimney-derive:publishAllPublicationsToIcTestRepository",
        ":kimney-compiler-plugin:publishAllPublicationsToIcTestRepository",
        gradle.includedBuild("kimney-gradle-plugin").task(":publishAllPublicationsToFunctionalRepository"),
    )
    val kimneyRepo = rootProject.layout.buildDirectory.dir("ic-repo").get().asFile.absolutePath
    val pluginRepo = rootProject.file("kimney-gradle-plugin/build/functional-repo").absolutePath
    val kimneyVersion = version.toString()
    inputs.property("kotlinUnderTest", kotlinUnderTest)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dkimney.icRepo=$kimneyRepo",
                "-Dkimney.pluginRepo=$pluginRepo",
                "-Dkimney.version=$kimneyVersion",
                "-Dkimney.kotlin=$kotlinUnderTest",
            )
        },
    )
}
