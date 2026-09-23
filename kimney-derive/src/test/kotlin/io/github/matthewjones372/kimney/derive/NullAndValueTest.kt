package io.github.matthewjones372.kimney.derive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class NullAndValueTest {

    private val model = FakeModel(
        constructions = mapOf(
            "AddressDto" to primary(param("street", "String")),
            "UserDto" to primary(param("name", "String"), param("address", "AddressDto?")),
            "ShapeDto.Circle" to primary(param("label", "String")),
        ),
        properties = mapOf(
            "Address" to mapOf("street" to "String"),
            "User" to mapOf("name" to "String?", "address" to "Address?"),
            "Shape.Circle" to mapOf("label" to "String?"),
        ),
        sealed = mapOf("Shape" to listOf("Shape.Circle"), "ShapeDto" to listOf("ShapeDto.Circle")),
        valueClasses = mapOf(
            "UserId" to param("raw", "Long"),
            "OrderId" to param("value", "Long"),
        ),
    )

    private fun lines(source: String, target: String): List<String> =
        derive(model, source, target).shouldBeInstanceOf<Derived.Failed>().failures.map { it.line }

    @Test
    fun `a non-null source reaches a nullable target through the non-null type`() {
        derive(model, "Address", "AddressDto?") shouldBe Derived.Planned(
            Plan.Construct("AddressDto", listOf(Arg.FromProperty("street", "street", Plan.Identity))),
        )
    }

    @Test
    fun `a nullable source reaches a nullable target behind a null check`() {
        derive(model, "Address?", "AddressDto?") shouldBe Derived.Planned(
            Plan.NullSafe(Plan.Construct("AddressDto", listOf(Arg.FromProperty("street", "street", Plan.Identity)))),
        )
    }

    @Test
    fun `a nullable top-level field into a non-null one says null is the reason, and how to fill it`() {
        lines("User", "UserDto") shouldBe listOf(
            "UserDto.name: String — User.name is String?, and a null has nowhere to go. " +
                "Make UserDto.name nullable, or fill it with .withFieldComputed(UserDto::name) { … }.",
        )
    }

    @Test
    fun `inside a sealed case the hint drops the override it cannot reach`() {
        lines("Shape", "ShapeDto") shouldBe listOf(
            "ShapeDto.Circle.label: String — Shape.Circle.label is String?, and a null has nowhere to go. " +
                "Make ShapeDto.Circle.label nullable.",
        )
    }

    @Test
    fun `a nullable root says so of the source itself`() {
        lines("String?", "String") shouldBe
            listOf("String — the source is String?, and a null has nowhere to go. Transform into String? instead.")
    }

    @Test
    fun `a value class target wraps, and a value class source unwraps`() {
        derive(model, "Long", "UserId") shouldBe Derived.Planned(Plan.Wrap("UserId", Plan.Identity))
        derive(model, "UserId", "Long") shouldBe Derived.Planned(Plan.Unwrap("UserId", "raw", Plan.Identity))
    }

    @Test
    fun `two value classes meet through what they hold, whatever their properties are called`() {
        derive(model, "UserId", "OrderId") shouldBe
            Derived.Planned(Plan.Wrap("OrderId", Plan.Unwrap("UserId", "raw", Plan.Identity)))
    }

    @Test
    fun `a value class whose inner type does not fit fails at its path like any pair`() {
        lines("UserId", "String") shouldBe listOf("String — no rule transforms Long into String.")
    }
}
