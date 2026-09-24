plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    // The runtime's binary surface as a file somebody reads in a diff.
    alias(libs.plugins.bcv)
    alias(libs.plugins.maven.publish) apply false
    base
}

// Handed to ktlint directly: Spotless does not read .editorconfig for every
// source set, and this is the one override that has to hold everywhere.
val ktlintOverrides = mapOf("ktlint_standard_kdoc" to "disabled")
val ktlintVersion: String = libs.versions.ktlint.get()

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
    }
}

kover {
    reports {
        total {
            verify {
                rule {
                    minBound(90)
                }
            }
            filters {
                excludes {
                    // The example proves the consumer path by compiling; it has
                    // no behaviour of its own to test.
                    classes("example.*")
                    // Run by JMH in forked JVMs, never by a test.
                    classes("kimney.benchmarks.*")
                }
            }
        }
    }
}

apiValidation {
    // Only the runtime is linked against by user code; the rest is loaded by
    // the compiler or is the example.
    ignoredProjects += listOf("kimney-derive", "kimney-compiler-plugin", "example", "benchmarks")
}

/** What each published artifact is, as a Maven search result should say. */
val published = mapOf(
    "kimney-runtime" to "The calls kimney's compiler plugin replaces: transformInto, the override chain, Partial.",
    "kimney-derive" to "kimney's derivation engine: source and target types to a plan, or every reason not.",
    "kimney-compiler-plugin" to "The K2 compiler plugin that derives transformations between Kotlin types.",
)

// The modules AGENTS.md promises depend on the standard library alone. Each
// one's NoThirdPartyDependenciesTest reads the classpath handed over here.
val stdlibOnly = setOf("kimney-runtime", "kimney-derive")

dependencies {
    subprojects.forEach { kover(project(it.path)) }
}

// The inner loop for engine and docs work: every check but the compiler plugin's
// tests, which are the one suite that compiles Kotlin per test. `build` still
// runs everything.
tasks.register("quickCheck") {
    group = "verification"
    description = "Runs every check except the compiler plugin's compile-and-run tests."
    dependsOn(subprojects.filter { it.name != "kimney-compiler-plugin" }.map { "${it.path}:check" })
    dependsOn(":kimney-compiler-plugin:detektMain", ":kimney-compiler-plugin:spotlessCheck", "spotlessCheck")
}

// The Gradle plugin is an included build, so a root publishToMavenLocal would
// otherwise publish every artifact but the one a consumer applies first.
tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes every kimney artifact, the Gradle plugin and its marker included, to Maven Local."
    dependsOn(gradle.includedBuild("kimney-gradle-plugin").task(":publishToMavenLocal"))
}

tasks.named("check") {
    dependsOn("koverVerify")
    // The Gradle plugin is an included build, which this one does not check
    // unless it is asked to.
    dependsOn(gradle.includedBuild("kimney-gradle-plugin").task(":check"))
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        "testImplementation"(libs.findLibrary("kotlin-test").get())
        "testImplementation"(libs.findLibrary("junit-jupiter").get())
        // Matchers and their failure messages; the tests still run on JUnit.
        "testImplementation"(libs.findLibrary("kotest-assertions").get())
        "testRuntimeOnly"(libs.findLibrary("junit-launcher").get())
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("junit.jupiter.execution.timeout.default", "60s")
    }

    if (name in stdlibOnly) {
        tasks.named<Test>("test") {
            // The main runtime classpath, not the test one, which carries JUnit.
            val mainRuntime = configurations.named("runtimeClasspath")
            inputs.files(mainRuntime).withPropertyName("mainRuntimeClasspath")
            jvmArgumentProviders.add(
                CommandLineArgumentProvider {
                    listOf(
                        "-Dkimney.runtimeClasspath=" + mainRuntime.get().joinToString(File.pathSeparator) {
                            it.name
                        },
                    )
                },
            )
        }
    }

    apply(plugin = "org.jetbrains.kotlinx.kover")

    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }
    // The plain `detekt` task cannot see types, so the rules that need them
    // are skipped there. `check` depends on the type-resolving pair instead.
    tasks.named("check") { dependsOn("detektMain", "detektTest") }

    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt", "test-fixtures/**/*.kt")
            ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
        }
    }

    published[name]?.let { summary ->
        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            // Central requires a javadoc jar; an empty one until KDoc rendering is decided (spec 0014).
            configure(
                com.vanniktech.maven.publish.KotlinJvm(
                    javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
                    sourcesJar = true,
                ),
            )
            pom { kimneyPom(this@subprojects.name, summary) }
        }
    }
}

/** The POM every kimney artifact shares; the Gradle plugin's build writes the same one. */
fun org.gradle.api.publish.maven.MavenPom.kimneyPom(artifact: String, summary: String) {
    name.set(artifact)
    description.set(summary)
    url.set("https://github.com/matthewjones372/kimney")
    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
    developers {
        developer {
            id.set("matthewjones372")
            name.set("Matt Jones")
        }
    }
    scm {
        url.set("https://github.com/matthewjones372/kimney")
        connection.set("scm:git:https://github.com/matthewjones372/kimney.git")
        developerConnection.set("scm:git:ssh://git@github.com/matthewjones372/kimney.git")
    }
}
