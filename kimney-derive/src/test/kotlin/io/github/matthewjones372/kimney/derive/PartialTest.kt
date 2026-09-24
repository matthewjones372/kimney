package io.github.matthewjones372.kimney.derive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class PartialTest {

    private val model = FakeModel(
        constructions = mapOf(
            "Command" to primary(param("customer", "Long"), param("address", "AddressDto")),
            "AddressDto" to primary(param("zip", "String")),
            "Order" to primary(param("quantity", "Quantity")),
        ),
        properties = mapOf(
            "Request" to mapOf("customer" to "Long?", "address" to "Address"),
            "Address" to mapOf("zip" to "String?"),
            "Line" to mapOf("quantity" to "Int"),
        ),
        valueClasses = mapOf("Quantity" to param("units", "Int")),
    )

    @Test
    fun `total mode still refuses a null into a non-null`() {
        derive(model, "Request", "Command").shouldBeInstanceOf<Derived.Failed>()
    }

    @Test
    fun `partial mode lets a null through as a check at its path, nested ones included, with constructors marked`() {
        derive(model, "Request", "Command", partial = true) shouldBe Derived.Planned(
            Plan.Construct(
                "Command",
                listOf(
                    Arg.FromProperty("customer", "customer", Plan.Required("Command.customer", Plan.Identity)),
                    Arg.FromProperty(
                        "address",
                        "address",
                        Plan.Construct(
                            "AddressDto",
                            listOf(Arg.FromProperty("zip", "zip", Plan.Required("Command.address.zip", Plan.Identity))),
                            guardedAt = "Command.address",
                        ),
                    ),
                ),
                guardedAt = "Command",
            ),
        )
    }

    @Test
    fun `a value class is guarded at the path of the field it fills`() {
        derive(model, "Line", "Order", partial = true) shouldBe Derived.Planned(
            Plan.Construct(
                "Order",
                listOf(
                    Arg.FromProperty("quantity", "quantity", Plan.Wrap("Quantity", Plan.Identity, "Order.quantity")),
                ),
                guardedAt = "Order",
            ),
        )
    }
}
