// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

data class Tree(val label: String, val children: List<Tree>)
data class TreeDto(val label: String, val children: List<TreeDto>)
data class TaggedTreeDto(val label: String, val tag: String, val children: List<TaggedTreeDto>)

fun tree(tree: Tree): TreeDto = tree.transformInto<TreeDto>()

fun tagged(tree: Tree): TaggedTreeDto = <!KIMNEY_CANNOT_TRANSFORM!>tree.transformInto<TaggedTreeDto>()<!>
