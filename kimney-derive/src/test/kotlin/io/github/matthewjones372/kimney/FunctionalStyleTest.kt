package io.github.matthewjones372.kimney

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Where mutable state is allowed to live, stated as a test. A ratchet, not a ban: a file that accumulates into a
 * mutable collection and hands back a read-only one is a builder, and earns an entry with its reason below.
 *
 * The build hands over the source roots and declares them as inputs of this task, so a violation cannot ride a
 * cached green build. See `kimney-derive/build.gradle.kts`.
 */
class FunctionalStyleTest {

    private val builders = mapOf<String, String>()

    private val accumulators = Regex(
        """\b(mutableListOf|mutableMapOf|mutableSetOf""" +
            """|LinkedHashMap|LinkedHashSet|IdentityHashMap|ArrayList|HashMap|HashSet)\s*[(<]""",
    )

    private fun handedOver(name: String): String {
        val value = System.getProperty(name)
        withClue("the build must pass -D$name; see kimney-derive/build.gradle.kts") { value.shouldNotBeNull() }
        return value!!
    }

    private fun repoRoot(): File = File(handedOver("kimney.style.repoRoot"))

    private fun sourceRoots(): List<File> = handedOver("kimney.style.sources")
        .split(File.pathSeparator)
        .filter { it.isNotBlank() }
        .map(::File)

    @Test
    fun `the build hands this test the sources it judges`() {
        val roots = sourceRoots()
        withClue("the build named no source roots at all") { roots.shouldNotBeEmpty() }

        val outside = roots.filterNot { it.absoluteFile.startsWith(repoRoot().absoluteFile) }
        withClue("a source root sits outside ${repoRoot()}, so the paths below cannot be keys: $outside") {
            outside.shouldBeEmpty()
        }
    }

    @Test
    fun `mutable collections are built only where a builder was meant to be`() {
        val root = repoRoot()
        val found = sourceRoots()
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
            .filter { accumulators.containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).path }
            .toSortedSet()

        val unexpected = found - builders.keys
        withClue("a mutable accumulator appeared outside a builder: $unexpected") { unexpected.shouldBeEmpty() }

        val stale = builders.keys - found
        withClue("these no longer accumulate; drop them from the list: $stale") { stale.shouldBeEmpty() }
    }
}
