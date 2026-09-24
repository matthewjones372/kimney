import io.github.matthewjones372.kimney.transformInto

data class Line(val sku: String)
data class LineDto(val sku: String)

data class Cart(val lines: MutableList<Line>, val tags: MutableSet<String>, val stock: MutableMap<String, Line>)
data class CartDto(val lines: List<LineDto>, val tags: MutableSet<String>, val stock: MutableMap<String, LineDto>)

data class Draft(val lines: MutableList<Line>)
data class DraftCopy(val lines: MutableList<Line>)

fun box(): String {
    val cart = Cart(mutableListOf(Line("a")), mutableSetOf("x"), mutableMapOf("k" to Line("b")))
    val dto = cart.transformInto<CartDto>()
    dto.tags += "y"
    val draft = Draft(mutableListOf(Line("a")))
    val copy = draft.transformInto<DraftCopy>()
    copy.lines += Line("new")
    return when {
        dto.lines != listOf(LineDto("a")) -> "Fail lines: ${dto.lines}"
        dto.stock != mapOf("k" to LineDto("b")) -> "Fail stock: ${dto.stock}"
        cart.tags != setOf("x") -> "Fail: the target set was the source's own: ${cart.tags}"
        draft.lines != listOf(Line("a")) -> "Fail: the copied list was the source's own: ${draft.lines}"
        else -> "OK"
    }
}
