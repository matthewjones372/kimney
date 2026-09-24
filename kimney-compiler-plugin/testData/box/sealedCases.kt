import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

sealed interface Shape {
    data class Circle(val radius: Double) : Shape
    data class Hexagon(val side: Double) : Shape
    data class Star(val points: Int) : Shape
    data class Square(val side: Double) : Shape
    data object Blob : Shape
}

sealed interface ShapeDto {
    data class Circle(val radius: Double) : ShapeDto
    data class Polygon(val side: Double, val sides: Int = 6) : ShapeDto
    data class Pointy(val tips: Int) : ShapeDto
    data object Unsupported : ShapeDto
}

data class Sketch(val shape: Shape, val layers: List<Shape>)
data class Drawing(val shape: ShapeDto, val layers: List<ShapeDto>)
data class Form(val id: Long?, val shape: Shape)
data class Stored(val id: Long, val shape: ShapeDto)

val star = Transformer<Shape.Star, ShapeDto> { ShapeDto.Pointy(it.points) }

fun Shape.toDto(): ShapeDto = into<_, ShapeDto>()
    .withSealedCaseRenamed(Shape.Hexagon::class, ShapeDto.Polygon::class)
    .withSealedFallback(ShapeDto.Unsupported)
    .transform()

fun Sketch.toDto(): Drawing = into<_, Drawing>()
    .withSealedCaseRenamed(Shape.Hexagon::class, ShapeDto.Polygon::class)
    .withTransformer(star)
    .withSealedFallback(ShapeDto.Unsupported)
    .transform()

fun Shape.hexagonIsSquare(): Shape =
    into<_, Shape>().withSealedCaseRenamed(Shape.Hexagon::class, Shape.Square::class).transform()

fun Form.validate(): Partial<Stored> = into<_, Stored>()
    .withSealedCaseRenamed(Shape.Hexagon::class, ShapeDto.Polygon::class)
    .withSealedFallback(ShapeDto.Unsupported)
    .transformPartial()

fun box(): String {
    val shapes = listOf(Shape.Circle(1.0), Shape.Hexagon(2.0), Shape.Star(5), Shape.Blob)
    val root = shapes.map { it.toDto() }
    val nested = Sketch(Shape.Star(5), shapes).toDto()
    val self = shapes.map { it.hexagonIsSquare() }
    val partial = Form(3, Shape.Hexagon(2.0)).validate()
    return when {
        root != listOf(ShapeDto.Circle(1.0), ShapeDto.Polygon(2.0), ShapeDto.Unsupported, ShapeDto.Unsupported) ->
            "Fail root: $root"
        nested != Drawing(
            ShapeDto.Pointy(5),
            listOf(ShapeDto.Circle(1.0), ShapeDto.Polygon(2.0), ShapeDto.Pointy(5), ShapeDto.Unsupported),
        ) -> "Fail nested: $nested"
        self != listOf(Shape.Circle(1.0), Shape.Square(2.0), Shape.Star(5), Shape.Blob) -> "Fail self: $self"
        partial != Partial.Ok(Stored(3, ShapeDto.Polygon(2.0))) -> "Fail partial: $partial"
        else -> "OK"
    }
}
