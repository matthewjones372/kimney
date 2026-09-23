import io.github.matthewjones372.kimney.transformInto

data class Line(val sku: String)
data class LineDto(val sku: String)

class Lines(private val skus: List<String>) : Iterable<Line> {
    override fun iterator(): Iterator<Line> = skus.map(::Line).iterator()
}

data class Basket(val lines: Iterable<Line>)
data class BasketDto(val lines: Iterable<LineDto>)

fun box(): String {
    val dto = Basket(Lines(listOf("x", "y"))).transformInto<BasketDto>()
    return if (dto.lines.toList() == listOf(LineDto("x"), LineDto("y"))) "OK" else "Fail: ${dto.lines.toList()}"
}
