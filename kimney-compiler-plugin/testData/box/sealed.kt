import io.github.matthewjones372.kimney.transformInto

sealed interface Shape {
    data class Circle(val radius: Double) : Shape
    data class Square(val side: Double) : Shape
    data object Empty : Shape
}

sealed interface ShapeDto {
    data class Circle(val radius: Double) : ShapeDto
    data class Square(val side: Double, val unit: String = "cm") : ShapeDto
    data object Empty : ShapeDto
    data class Hexagon(val side: Double) : ShapeDto
}

data class Drawing(val name: String, val shape: Shape)
data class DrawingDto(val name: String, val shape: ShapeDto)

fun box(): String {
    val shapes = listOf(Shape.Circle(1.0), Shape.Square(2.0), Shape.Empty).map { it.transformInto<ShapeDto>() }
    val expected = listOf(ShapeDto.Circle(1.0), ShapeDto.Square(2.0, "cm"), ShapeDto.Empty)
    val drawing = Drawing("d", Shape.Square(3.0)).transformInto<DrawingDto>()
    return when {
        shapes != expected -> "Fail: $shapes"
        drawing != DrawingDto("d", ShapeDto.Square(3.0)) -> "Fail: $drawing"
        else -> "OK"
    }
}
