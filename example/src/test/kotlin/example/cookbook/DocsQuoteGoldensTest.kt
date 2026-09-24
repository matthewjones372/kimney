package example.cookbook

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every kimney message a page quotes must be one kimney produces. A quote is a line of a plain code block, or a
 * backticked table cell, reading `<path> — <reason>`; it must appear verbatim in some golden under `testData/`.
 * A quote ending in `…` is a deliberate prefix.
 */
class DocsQuoteGoldensTest {

    private val root = File(System.getProperty("kimney.repoRoot").shouldNotBeNull())

    private val goldens = root.resolve("kimney-compiler-plugin/testData").walkTopDown()
        .filter { it.isFile && it.name.endsWith(".diag.txt") }
        .joinToString("\n") { it.readText() }

    private val pages = listOf("README.md") +
        root.resolve("docs").listFiles().orEmpty().filter { it.extension == "md" }.map { "docs/${it.name}" }

    private val cell = Regex("^`([^`]+ — [^`]+)`$")

    // A compiler's own `e: File.kt:1:2 ` prefix is illustration; the message after it is the claim.
    private fun message(line: String): String = line.trim().replace(Regex("^e: \\S+ "), "")

    private fun quotes(page: String): List<String> {
        val text = root.resolve(page).readText()
        val inBlocks = plainBlockLines(text).filter { " — " in it }
        val inTables = text.lines().filter { it.startsWith("|") }
            .flatMap { row -> row.split("|").mapNotNull { cell.find(it.trim())?.groupValues?.get(1) } }
        return (inBlocks + inTables).map(::message).toList()
    }

    /** Lines inside fences opened with no language, read fence by fence so prose between blocks is never one. */
    private fun plainBlockLines(text: String): List<String> =
        text.lines().fold(Pair<String?, List<String>>(null, emptyList())) { (open, kept), line ->
            when {
                open == null && line.startsWith("```") -> line.removePrefix("```").trim() to kept
                open != null && line.trim() == "```" -> null to kept
                open == "" -> open to kept + line
                else -> open to kept
            }
        }.second

    private fun produced(quote: String): Boolean =
        if (quote.endsWith("…")) quote.dropLast(1).trimEnd() in goldens else quote in goldens

    @Test
    fun `every message the docs quote is one kimney produces`() {
        val all = pages.flatMap { page -> quotes(page).map { page to it } }
        all shouldHaveAtLeastSize 20

        val invented = all.filterNot { (_, quote) -> produced(quote) }
        withClue("these quotes match no golden under testData; copy them from the golden instead") {
            invented.map { (page, quote) -> "$page: $quote" }.shouldBeEmpty()
        }
    }
}
