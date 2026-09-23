package io.github.matthewjones372.kimney

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.io.File

class NoThirdPartyDependenciesTest {

    /** What the Kotlin plugin puts on every module. A name added here wants a spec behind it. */
    private val allowed = listOf("kotlin-stdlib", "annotations-")

    @Test
    fun `the main runtime classpath is the kotlin standard library and nothing else`() {
        val raw = System.getProperty("kimney.runtimeClasspath")
        withClue("the build must pass -Dkimney.runtimeClasspath; see build.gradle.kts") { raw.shouldNotBeNull() }

        val unexpected = raw!!.split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .filterNot { entry -> allowed.any { entry.startsWith(it) } }

        withClue("kimney-runtime takes no runtime dependency beyond $allowed, but found: $unexpected") {
            unexpected.shouldBeEmpty()
        }
    }
}
