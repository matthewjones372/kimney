// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.into
import kotlin.reflect.KClass

sealed interface Shape {
    data class Circle(val radius: Double) : Shape
    data class Hexagon(val side: Double) : Shape
}

sealed interface ShapeDto {
    data class Circle(val radius: Double) : ShapeDto
    data class Polygon(val side: Double) : ShapeDto
    data object Unsupported : ShapeDto
}

sealed interface Other {
    data object Unknown : Other
}

val hexagon: KClass<Shape.Hexagon> = Shape.Hexagon::class
val unsupported: ShapeDto = ShapeDto.Unsupported

fun notALiteral(shape: Shape): ShapeDto = <!KIMNEY_CANNOT_TRANSFORM!>shape.into<_, ShapeDto>()
    .withSealedCaseRenamed(hexagon, ShapeDto.Polygon::class)
    .transform()<!>

fun notAnObject(shape: Shape): ShapeDto = <!KIMNEY_CANNOT_TRANSFORM!>shape.into<_, ShapeDto>()
    .withSealedFallback(unsupported)
    .transform()<!>

fun twice(shape: Shape): ShapeDto = <!KIMNEY_CANNOT_TRANSFORM!>shape.into<_, ShapeDto>()
    .withSealedCaseRenamed(Shape.Hexagon::class, ShapeDto.Polygon::class)
    .withSealedCaseRenamed(Shape.Hexagon::class, ShapeDto.Circle::class)
    .transform()<!>

fun unusedRename(shape: Shape): ShapeDto = <!KIMNEY_UNUSED_SEALED_MAPPING!>shape.into<_, ShapeDto>()
    .withSealedFallback(ShapeDto.Unsupported)
    .withSealedCaseRenamed(ShapeDto.Circle::class, Shape.Circle::class)<!>
    .transform()

fun unusedFallback(shape: Shape): ShapeDto = <!KIMNEY_UNUSED_SEALED_MAPPING!>shape.into<_, ShapeDto>()
    .withSealedFallback(Other.Unknown)<!>
    .withSealedFallback(ShapeDto.Unsupported)
    .transform()
