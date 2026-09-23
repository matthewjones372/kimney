import io.github.matthewjones372.kimney.transformInto

@JvmInline value class UserId(val raw: Long)

data class Line(val sku: String)
data class LineDto(val sku: String)

class History(val lines: Array<Line>, val ids: Array<UserId>)
class HistoryDto(val lines: Array<LineDto>, val ids: Array<Long>)

fun box(): String {
    val dto = History(arrayOf(Line("a"), Line("b")), arrayOf(UserId(1), UserId(2))).transformInto<HistoryDto>()
    return when {
        !dto.lines.contentEquals(arrayOf(LineDto("a"), LineDto("b"))) -> "Fail lines: ${dto.lines.toList()}"
        !dto.ids.contentEquals(arrayOf(1L, 2L)) -> "Fail ids: ${dto.ids.toList()}"
        dto.lines.javaClass.componentType != LineDto::class.java -> "Fail type: ${dto.lines.javaClass}"
        else -> "OK"
    }
}
