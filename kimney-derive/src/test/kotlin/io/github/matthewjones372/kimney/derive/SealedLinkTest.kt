package io.github.matthewjones372.kimney.derive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class SealedLinkTest {

    private val model = FakeModel(
        constructions = mapOf(
            "ShapeDto.Circle" to primary(param("radius", "Double")),
            "ShapeDto.Polygon" to primary(param("side", "Double")),
            "Drawing" to primary(param("shape", "ShapeDto")),
        ),
        properties = mapOf(
            "Shape.Circle" to mapOf("radius" to "Double"),
            "Shape.Hexagon" to mapOf("side" to "Double"),
            "Sketch" to mapOf("shape" to "Shape"),
        ),
        supertypes = mapOf(
            "ShapeDto.Circle" to setOf("ShapeDto"),
            "ShapeDto.Polygon" to setOf("ShapeDto"),
            "ShapeDto.Unsupported" to setOf("ShapeDto"),
            "Shape.Circle" to setOf("Shape"),
            "Shape.Hexagon" to setOf("Shape"),
            "Shape.Blob" to setOf("Shape"),
        ),
        sealed = mapOf(
            "Shape" to listOf("Shape.Circle", "Shape.Hexagon", "Shape.Blob"),
            "ShapeDto" to listOf("ShapeDto.Circle", "ShapeDto.Polygon", "ShapeDto.Unsupported"),
        ),
        objects = setOf("Shape.Blob", "ShapeDto.Unsupported"),
    )

    private val circle = Arm(
        "Shape.Circle",
        "ShapeDto.Circle",
        Plan.Construct("ShapeDto.Circle", listOf(Arg.FromProperty("radius", "radius", Plan.Identity))),
    )
    private val hexagonAsPolygon = Arm(
        "Shape.Hexagon",
        "ShapeDto.Polygon",
        Plan.Construct("ShapeDto.Polygon", listOf(Arg.FromProperty("side", "side", Plan.Identity))),
    )

    private val rename = SealedOverride.Renamed("Shape.Hexagon", "ShapeDto.Polygon", 0)
    private val fallback = SealedOverride.Fallback("ShapeDto.Unsupported", 1)

    @Test
    fun `a renamed case is derived into the case named, by every rule, and the rest match by name`() {
        derive(model, "Shape", "ShapeDto", sealed = listOf(rename, fallback)) shouldBe Derived.Planned(
            Plan.SealedByName(
                "ShapeDto",
                listOf(
                    circle,
                    hexagonAsPolygon,
                    Arm("Shape.Blob", "ShapeDto.Unsupported", Plan.ObjectInstance("ShapeDto.Unsupported")),
                ),
                otherwise = "ShapeDto.Unsupported",
                uses = setOf(0, 1),
            ),
        )
    }

    @Test
    fun `a transformer from the case into the target parent builds it, before the fallback`() {
        val byHand = Supplied("Shape.Hexagon", "ShapeDto", index = 2)
        val derived = derive(model, "Sketch", "Drawing", transformers = listOf(byHand), sealed = listOf(fallback))

        derived.shouldBeInstanceOf<Derived.Planned<String>>().plan.linksUsed() shouldBe setOf(1, 2)
    }

    @Test
    fun `a transformer into the parent is not tried for a case with a match`() {
        val broad = Supplied("Shape.Circle", "ShapeDto", index = 2)
        val derived =
            derive(model, "Sketch", "Drawing", transformers = listOf(broad), sealed = listOf(rename, fallback))

        derived.shouldBeInstanceOf<Derived.Planned<String>>().plan.linksUsed() shouldBe setOf(0, 1)
    }

    @Test
    fun `without a way out each unmatched case offers all three`() {
        derive(model, "Shape", "ShapeDto", sealed = listOf(rename)).shouldBeInstanceOf<Derived.Failed>()
            .failures.map { it.line } shouldBe listOf(
            "ShapeDto — Shape.Blob has no subclass of the same name in ShapeDto. Map it with " +
                ".withSealedCaseRenamed(Shape.Blob::class, ShapeDto.….class), build it with " +
                ".withTransformer(Transformer<Shape.Blob, ShapeDto> { … }), or send every unmatched case to " +
                "one object with .withSealedFallback(ShapeDto.…).",
        )
    }

    @Test
    fun `a fallback that is not an object, or not a case of the target, is not used`() {
        val notObject = SealedOverride.Fallback("ShapeDto.Polygon", 3)
        val elsewhere = SealedOverride.Fallback("Other.Unknown", 4)

        derive(model, "Shape", "ShapeDto", sealed = listOf(rename, notObject, elsewhere, fallback))
            .shouldBeInstanceOf<Derived.Planned<String>>().plan.linksUsed() shouldBe setOf(0, 1)
    }

    @Test
    fun `a sealed type into itself is mapped when a link names its cases`() {
        val self = SealedOverride.Renamed("Shape.Hexagon", "Shape.Circle", 0)
        val derived = derive(model, "Shape", "Shape", sealed = listOf(self))

        // Mapped, not passed through: the renamed case is built, and this model cannot build a Shape.Circle.
        derived.shouldBeInstanceOf<Derived.Failed>().failures.map { it.path.toString() } shouldBe
            listOf("Shape.Hexagon")
        derive(model, "Shape", "Shape") shouldBe Derived.Planned(Plan.Identity)
    }

    @Test
    fun `two renames of one case and two fallbacks are named, by their places in the chain`() {
        val again = SealedOverride.Renamed("Shape.Hexagon", "ShapeDto.Circle", 2)
        val links = listOf(rename, fallback, again, fallback.copy(index = 3))

        derive(model, "Shape", "ShapeDto", sealed = links).shouldBeInstanceOf<Derived.Failed>()
            .failures.map { it.line } shouldBe listOf(
            "ShapeDto — Shape.Hexagon is renamed twice, by withSealedCaseRenamed #1 and #3. Keep one.",
            "ShapeDto — ShapeDto falls back twice, by withSealedFallback #2 and #4. Keep one.",
        )
    }
}
