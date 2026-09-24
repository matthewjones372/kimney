import io.github.matthewjones372.kimney.transformInto

data class Point(val x: Int)
data class PointDto(val x: Int)

// Any file the plugin lowers may call local functions, with kimney involved or not.
fun box(): String {
    fun twice(n: Int): Int = n * 2
    fun toDto(p: Point): PointDto = p.transformInto<PointDto>()
    val dto = toDto(Point(twice(21)))
    return if (dto == PointDto(42)) "OK" else "Fail: $dto"
}
