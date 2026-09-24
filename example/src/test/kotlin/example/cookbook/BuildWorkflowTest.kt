package example.cookbook

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/** The Kotlins CI runs the compiler tests on are the ones the Gradle plugin says kimney is tested on. */
class BuildWorkflowTest {
    private val root = File(System.getProperty("kimney.repoRoot").shouldNotBeNull())

    @Test
    fun `the CI matrix is kimney's tested Kotlins`() {
        val tested = Properties().apply { root.resolve("gradle.properties").reader().use(::load) }
            .getProperty("kimney.kotlinTested").split(',')
        val matrix = root.resolve(".github/workflows/build.yml").readLines()
            .single { it.trim().startsWith("kotlin: [") }
            .substringAfter('[').substringBefore(']').split(',').map { it.trim().trim('"') }

        matrix shouldBe tested
    }
}
