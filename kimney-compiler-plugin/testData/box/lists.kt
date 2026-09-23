// CHECK_BYTECODE_TEXT
// 0 LambdaMetafactory
// 0 INVOKEINTERFACE kotlin/jvm/functions/Function1.invoke
import io.github.matthewjones372.kimney.transformInto

enum class Tag { C, A, B }
enum class TagDto { A, B, C }

data class Line(val sku: String, val note: String?)
data class LineDto(val sku: String, val note: String?)

data class Order(
    val lines: List<Line>,
    val all: List<Line>,
    val grid: List<List<Line>>,
    val tags: Set<Tag>,
    val notes: List<String?>,
)
data class OrderDto(
    val lines: List<LineDto>,
    val all: Iterable<LineDto>,
    val grid: Collection<List<LineDto>>,
    val tags: Set<TagDto>,
    val notes: List<String?>,
)

fun box(): String {
    val a = Line("a", null)
    val b = Line("b", "gift")
    val dto = Order(listOf(a, b), listOf(b), listOf(listOf(a), emptyList()), linkedSetOf(Tag.C, Tag.A), listOf(null, "x"))
        .transformInto<OrderDto>()
    val la = LineDto("a", null)
    val lb = LineDto("b", "gift")
    return when {
        dto.lines != listOf(la, lb) -> "Fail lines: ${dto.lines}"
        dto.all.toList() != listOf(lb) -> "Fail all: ${dto.all}"
        dto.grid.toList() != listOf(listOf(la), emptyList()) -> "Fail grid: ${dto.grid}"
        dto.tags.toList() != listOf(TagDto.C, TagDto.A) -> "Fail tags order: ${dto.tags}"
        dto.notes != listOf(null, "x") -> "Fail notes: ${dto.notes}"
        else -> "OK"
    }
}
