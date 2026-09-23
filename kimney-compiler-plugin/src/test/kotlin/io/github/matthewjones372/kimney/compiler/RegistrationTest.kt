package io.github.matthewjones372.kimney.compiler

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

/** The compiler finds both classes through `META-INF/services`, and matches them to the Gradle plugin by id. */
class RegistrationTest {

    @Test
    fun `the compiler finds the registrar and it runs on K2`() {
        val registrars = ServiceLoader.load(CompilerPluginRegistrar::class.java).toList()

        registrars.map { it::class } shouldContainExactly listOf(KimneyRegistrar::class)
        registrars.single().supportsK2 shouldBe true
    }

    @Test
    fun `the command line processor answers to the same id as the registrar`() {
        val processors = ServiceLoader.load(CommandLineProcessor::class.java).toList()

        processors.map { it::class } shouldContainExactly listOf(KimneyCommandLineProcessor::class)
        processors.single().pluginId shouldBe KimneyRegistrar().pluginId
        processors.single().pluginOptions shouldBe emptyList()
    }
}
