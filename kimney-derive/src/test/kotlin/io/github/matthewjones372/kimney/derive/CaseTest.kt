package io.github.matthewjones372.kimney.derive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class CaseTest {

    private val statuses = FakeModel(
        constructions = emptyMap(),
        enums = mapOf(
            "Status" to listOf("ACTIVE", "SUSPENDED"),
            "StatusDto" to listOf("ACTIVE", "SUSPENDED", "UNKNOWN"),
            "Narrow" to listOf("ACTIVE"),
        ),
    )

    private val shapes = FakeModel(
        constructions = mapOf(
            "ShapeDto.Circle" to primary(param("radius", "Double")),
            "ShapeDto.Square" to primary(param("side", "Double"), param("unit", "String", hasDefault = true)),
        ),
        properties = mapOf(
            "Shape.Circle" to mapOf("radius" to "Double"),
            "Shape.Square" to mapOf("side" to "Double"),
            "Bad.Circle" to mapOf("radius" to "Int"),
        ),
        sealed = mapOf(
            "Shape" to listOf("Shape.Circle", "Shape.Square", "Shape.Empty"),
            "ShapeDto" to listOf("ShapeDto.Circle", "ShapeDto.Square", "ShapeDto.Empty", "ShapeDto.Hexagon"),
            "Bad" to listOf("Bad.Circle", "Bad.Oval"),
            "Narrow" to listOf("Narrow.Circle"),
        ),
        objects = setOf("Shape.Empty", "ShapeDto.Empty"),
    )

    @Test
    fun `an enum maps entry to entry by name, and extra target entries are fine`() {
        derive(statuses, "Status", "StatusDto") shouldBe
            Derived.Planned(Plan.EnumByName("Status", "StatusDto", listOf("ACTIVE", "SUSPENDED")))
    }

    @Test
    fun `every source entry without a target entry is named`() {
        val failed = derive(statuses, "Status", "Narrow").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.map { it.line } shouldBe listOf(
            "Narrow — Status.SUSPENDED has no entry of the same name in Narrow.",
        )
    }

    @Test
    fun `a sealed type maps case to case by name, deriving each pair by every rule`() {
        derive(shapes, "Shape", "ShapeDto") shouldBe Derived.Planned(
            Plan.SealedByName(
                "ShapeDto",
                listOf(
                    Arm(
                        "Shape.Circle",
                        "ShapeDto.Circle",
                        Plan.Construct("ShapeDto.Circle", listOf(Arg.FromProperty("radius", "radius", Plan.Identity))),
                    ),
                    Arm(
                        "Shape.Square",
                        "ShapeDto.Square",
                        Plan.Construct(
                            "ShapeDto.Square",
                            listOf(Arg.FromProperty("side", "side", Plan.Identity), Arg.Default("unit")),
                        ),
                    ),
                    Arm("Shape.Empty", "ShapeDto.Empty", Plan.ObjectInstance("ShapeDto.Empty")),
                ),
            ),
        )
    }

    @Test
    fun `missing subclasses and failures inside a case are all reported, the latter through the case`() {
        val failed = derive(shapes, "Bad", "ShapeDto").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.map { it.line } shouldBe listOf(
            "ShapeDto.Circle.radius: Double — no rule transforms Int into Double.",
            "ShapeDto — Bad.Oval has no subclass of the same name in ShapeDto.",
        )
    }

    @Test
    fun `an object target comes only from an object source`() {
        val failed = derive(shapes, "Shape.Circle", "ShapeDto.Empty").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.single().line shouldBe
            "ShapeDto.Empty — no rule transforms Shape.Circle into ShapeDto.Empty."
    }

    @Test
    fun `enums and sealed types do not cross`() {
        val model = FakeModel(
            constructions = emptyMap(),
            enums = mapOf("Status" to listOf("ACTIVE")),
            sealed = mapOf("Narrow" to listOf("Narrow.ACTIVE")),
        )

        derive(model, "Status", "Narrow").shouldBeInstanceOf<Derived.Failed>().failures.single().line shouldBe
            "Narrow — no rule transforms Status into Narrow."
    }

    @Test
    fun `overrides on a target that is not constructed are refused rather than dropped`() {
        val failed = derive(statuses, "Status", "StatusDto", listOf(Override.Renamed("name", "label")))
            .shouldBeInstanceOf<Derived.Failed>()

        failed.failures.single().line shouldBe
            "StatusDto.name — withFieldRenamed names 'name', which is not a constructor parameter of StatusDto."
    }

    @Test
    fun `with overrides, a source of the target's own type is rebuilt rather than passed through`() {
        val model = FakeModel(
            constructions = mapOf("User" to primary(param("name", "String"), param("age", "Int"))),
            properties = mapOf("User" to mapOf("name" to "String", "age" to "Int")),
        )

        derive(model, "User", "User", listOf(Override.Const("name", "String", 0))) shouldBe Derived.Planned(
            Plan.Construct("User", listOf(Arg.Const("name", 0), Arg.FromProperty("age", "age", Plan.Identity))),
        )
    }
}
