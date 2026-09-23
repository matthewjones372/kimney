// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

data class Tree(val label: String, val child: Tree)
data class TreeDto(val label: String, val child: TreeDto)

fun tree(tree: Tree): TreeDto = <!KIMNEY_CANNOT_TRANSFORM!>tree.transformInto<TreeDto>()<!>
