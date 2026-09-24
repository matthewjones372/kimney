package example.cookbook

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A Kotlin block in the README or the cookbook whose first line is `// file: <path>` quotes that file, and must
 * quote it verbatim: the file is what the build compiles and [CookbookTest] runs, so a block that drifts from it
 * is a claim nothing checks.
 */
class DocsMatchTheirFilesTest {

    private val root = File(
        System.getProperty("kimney.repoRoot").shouldNotBeNull(),
    )

    private val quoted = Regex("```kotlin\n// file: (\\S+)\n(.*?)\n```", RegexOption.DOT_MATCHES_ALL)

    private fun blocks(page: String): List<Pair<String, String>> =
        quoted.findAll(root.resolve(page).readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test
    fun `every file the cookbook quotes is quoted exactly`() {
        val blocks = blocks("docs/cookbook.md")

        blocks shouldHaveAtLeastSize 10
        blocks.forEach { (path, body) ->
            withClue("docs/cookbook.md quotes $path") { body shouldBe root.resolve(path).readText().trimEnd('\n') }
        }
    }

    @Test
    fun `every file the README quotes is quoted exactly`() {
        val blocks = blocks("README.md")

        blocks shouldHaveAtLeastSize 2
        blocks.forEach { (path, body) ->
            withClue("README.md quotes $path") { body shouldBe root.resolve(path).readText().trimEnd('\n') }
        }
    }
}
