package io.github.matthewjones372.kimney.gradle

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KotlinVersionTest {

    @Test
    fun `the Kotlin kimney is built for is accepted`() {
        kotlinMismatch(builtFor = "2.4.10", found = "2.4.10").shouldBeNull()
    }

    @Test
    fun `any other Kotlin is refused, naming both versions and the way out`() {
        kotlinMismatch(builtFor = "2.4.10", found = "2.4.20") shouldBe
            "kimney ${BuildConfig.KIMNEY_VERSION} is built for Kotlin 2.4.10; this build uses 2.4.20. " +
            "Use Kotlin 2.4.10, or a kimney release built for 2.4.20."
    }

    @Test
    fun `a build on another Kotlin fails at configuration with that message`(@TempDir dir: File) {
        val repo = System.getProperty("kimney.functionalRepo")
        val version = System.getProperty("kimney.version")
        dir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    maven(url = "${File(repo).toURI()}")
                    gradlePluginPortal()
                }
            }
            rootProject.name = "other-kotlin"
            """.trimIndent(),
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "$OTHER_KOTLIN"
                id("io.github.matthewjones372.kimney") version "$version"
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create().withProjectDir(dir).withArguments("help").buildAndFail()

        result.output shouldContain "is built for Kotlin ${BuildConfig.KOTLIN_VERSION}; this build uses $OTHER_KOTLIN."
    }

    private companion object {
        const val OTHER_KOTLIN = "2.4.20"
    }
}
