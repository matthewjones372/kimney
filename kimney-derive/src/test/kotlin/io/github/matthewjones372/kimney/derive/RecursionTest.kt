package io.github.matthewjones372.kimney.derive

import io.github.matthewjones372.kimney.derive.Container.Kind
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RecursionTest {

    private val model = FakeModel(
        constructions = mapOf(
            "TreeDto" to primary(param("label", "String"), param("children", "List<TreeDto>")),
            "NodeDto" to primary(param("next", "NodeDto?")),
            "FolderDto" to primary(param("files", "List<FileDto>")),
            "FileDto" to primary(param("parent", "FolderDto?")),
        ),
        properties = mapOf(
            "Tree" to mapOf("label" to "String", "children" to "List<Tree>"),
            "Node" to mapOf("next" to "Node?"),
            "Folder" to mapOf("files" to "List<File>"),
            "File" to mapOf("parent" to "Folder?"),
        ),
        containers = mapOf(
            "List<Tree>" to Container(Kind.LIST, "Tree"),
            "List<TreeDto>" to Container(Kind.LIST, "TreeDto"),
            "List<File>" to Container(Kind.LIST, "File"),
            "List<FileDto>" to Container(Kind.LIST, "FileDto"),
        ),
    )

    private fun tree(depth: Int, label: Arg<String>) = Plan.Named(
        depth,
        "Tree",
        "TreeDto",
        Plan.Construct(
            "TreeDto",
            listOf(
                label,
                Arg.FromProperty(
                    "children",
                    "children",
                    Plan.Elements(Kind.LIST, "List<TreeDto>", Plan.Reference(depth)),
                ),
            ),
        ),
    )

    @Test
    fun `a pair that meets itself becomes a named plan and a reference to it`() {
        derive(model, "Tree", "TreeDto") shouldBe
            Derived.Planned(tree(0, Arg.FromProperty("label", "label", Plan.Identity)))
    }

    @Test
    fun `an optional self is the same, behind its null check`() {
        derive(model, "Node", "NodeDto") shouldBe Derived.Planned(
            Plan.Named(
                0,
                "Node",
                "NodeDto",
                Plan.Construct("NodeDto", listOf(Arg.FromProperty("next", "next", Plan.NullSafe(Plan.Reference(0))))),
            ),
        )
    }

    @Test
    fun `two pairs that reach each other name only the one that recurs`() {
        val parent = Arg.FromProperty("parent", "parent", Plan.NullSafe(Plan.Reference(0)))
        val file = Plan.Construct("FileDto", listOf(parent))

        derive(model, "Folder", "FolderDto") shouldBe Derived.Planned(
            Plan.Named(
                0,
                "Folder",
                "FolderDto",
                Plan.Construct(
                    "FolderDto",
                    listOf(Arg.FromProperty("files", "files", Plan.Elements(Kind.LIST, "List<FileDto>", file))),
                ),
            ),
        )
    }

    @Test
    fun `overrides on a recursive root stay at the root, and the recursion below is named where it recurs`() {
        val overrides = listOf(Override.Const("label", "String", 0))
        // Under the root, the first pair to meet itself is the list, so the list is what gets a name.
        val children = Plan.Named(
            1,
            "List<Tree>",
            "List<TreeDto>",
            Plan.Elements(
                Kind.LIST,
                "List<TreeDto>",
                Plan.Construct(
                    "TreeDto",
                    listOf(
                        Arg.FromProperty("label", "label", Plan.Identity),
                        Arg.FromProperty("children", "children", Plan.Reference(1)),
                    ),
                ),
            ),
        )

        derive(model, "Tree", "TreeDto", overrides) shouldBe Derived.Planned(
            Plan.Construct(
                "TreeDto",
                listOf(Arg.Const("label", 0), Arg.FromProperty("children", "children", children)),
            ),
        )
    }
}
