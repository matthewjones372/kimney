package io.github.matthewjones372.kimney.gradle

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KotlinVersionTest {
    private val tested = listOf("2.4.0", "2.4.10", "2.4.20")

    @Test
    fun `every tested Kotlin is supported`() {
        tested.forEach { checkKotlin(tested, found = it) shouldBe KotlinCheck.Supported }
    }

    @Test
    fun `a prerelease of a tested patch is supported`() {
        checkKotlin(tested, found = "2.4.20-RC3") shouldBe KotlinCheck.Supported
    }

    @Test
    fun `a newer patch of the tested minor is applied, with a warning`() {
        checkKotlin(tested, found = "2.4.30") shouldBe KotlinCheck.Untested(
            "kimney ${BuildConfig.KIMNEY_VERSION} is tested on Kotlin 2.4.0 to 2.4.20; this build uses 2.4.30, " +
                "which it has not been tested on.",
        )
    }

    @Test
    fun `another minor is refused, naming the range and the way out`() {
        checkKotlin(tested, found = "2.5.0") shouldBe KotlinCheck.Unsupported(
            "kimney ${BuildConfig.KIMNEY_VERSION} supports Kotlin 2.4.0 to 2.4.20; this build uses 2.5.0. " +
                "Use one of those, or a kimney release built for 2.5.",
        )
        checkKotlin(tested, found = "2.3.21") shouldBe KotlinCheck.Unsupported(
            "kimney ${BuildConfig.KIMNEY_VERSION} supports Kotlin 2.4.0 to 2.4.20; this build uses 2.3.21. " +
                "Use one of those, or a kimney release built for 2.3.",
        )
    }

    @Test
    fun `a version that is not three numbers is refused as itself`() {
        checkKotlin(tested, found = "2.4") shouldBe KotlinCheck.Unsupported(
            "kimney ${BuildConfig.KIMNEY_VERSION} supports Kotlin 2.4.0 to 2.4.20; this build uses 2.4. " +
                "Use one of those, or a kimney release built for 2.4.",
        )
    }

    @Test
    fun `the plugin is built with the tested list`() {
        BuildConfig.KOTLIN_TESTED.split(',') shouldBe tested
    }

    @Test
    fun `a build on another minor fails at configuration with that message`(@TempDir dir: File) {
        val result = build(dir, kotlin = "2.3.21").buildAndFail()

        result.output shouldContain "supports Kotlin 2.4.0 to 2.4.20; this build uses 2.3.21."
    }

    @Test
    fun `a build on the oldest tested Kotlin applies it without a word`(@TempDir dir: File) {
        val result = build(dir, kotlin = "2.4.0").build()

        result.output shouldNotContain "kimney"
    }

    private fun build(dir: File, kotlin: String): GradleRunner {
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
            rootProject.name = "kotlin-$kotlin"
            """.trimIndent(),
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlin"
                id("io.github.matthewjones372.kimney") version "$version"
            }
            """.trimIndent(),
        )
        return GradleRunner.create().withProjectDir(dir).withArguments("help")
    }
}
