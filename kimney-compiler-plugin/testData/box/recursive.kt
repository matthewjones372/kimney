import io.github.matthewjones372.kimney.transformInto

data class Tree(val label: String, val children: List<Tree>)
data class TreeDto(val label: String, val children: List<TreeDto>)

data class Node(val value: Int, val next: Node?)
data class NodeDto(val value: Int, val next: NodeDto?)

fun box(): String {
    val tree = Tree("root", listOf(Tree("a", emptyList()), Tree("b", listOf(Tree("c", emptyList())))))
    val dto = tree.transformInto<TreeDto>()
    val expected = TreeDto("root", listOf(TreeDto("a", emptyList()), TreeDto("b", listOf(TreeDto("c", emptyList())))))
    val list = Node(1, Node(2, Node(3, null))).transformInto<NodeDto>()
    return when {
        dto != expected -> "Fail tree: $dto"
        list != NodeDto(1, NodeDto(2, NodeDto(3, null))) -> "Fail list: $list"
        else -> "OK"
    }
}
