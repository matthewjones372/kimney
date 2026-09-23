plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
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
