plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    // The runtime's binary surface as a file somebody reads in a diff.
    alias(libs.plugins.bcv)
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
                    // A `main` that prints. There is nothing in it to assert.
                    classes("example.MainKt")
                }
            }
        }
    }
}

apiValidation {
    // Only the runtime is linked against by user code; the rest is loaded by
    // the compiler or is the example.
    ignoredProjects += listOf("kimney-derive", "kimney-compiler-plugin", "example")
}

// The modules AGENTS.md promises depend on the standard library alone. Each
// one's NoThirdPartyDependenciesTest reads the classpath handed over here.
val stdlibOnly = setOf("kimney-runtime", "kimney-derive")

dependencies {
    subprojects.forEach { kover(project(it.path)) }
}

tasks.named("check") {
    dependsOn("koverVerify")
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
}
