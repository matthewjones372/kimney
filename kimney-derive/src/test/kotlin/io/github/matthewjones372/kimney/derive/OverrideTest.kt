package io.github.matthewjones372.kimney.derive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class OverrideTest {

    private val model = FakeModel(
        constructions = mapOf(
            "UserDto" to primary(param("name", "String"), param("source", "String"), param("age", "Long")),
        ),
        properties = mapOf("User" to mapOf("fullName" to "String", "born" to "Year", "age" to "Int")),
        supertypes = mapOf("Nothing" to setOf("String", "Long")),
    )

    private fun lines(overrides: List<Override<String>>): List<String> =
        derive(model, "User", "UserDto", overrides).shouldBeInstanceOf<Derived.Failed>().failures.map { it.line }

    @Test
    fun `each override fills its field, and wins over a same-named source`() {
        val overrides = listOf(
            Override.Renamed("name", from = "fullName"),
            Override.Const("source", valueType = "String", index = 1),
            Override.Computed("age", resultType = "Long", index = 2),
        )

        derive(model, "User", "UserDto", overrides) shouldBe Derived.Planned(
            Plan.Construct(
                "UserDto",
                listOf(
                    Arg.FromProperty("name", "fullName", Plan.Identity),
                    Arg.Const("source", 1),
                    Arg.Computed("age", 2),
                ),
            ),
        )
    }

    @Test
    fun `an override naming no constructor parameter says so`() {
        val overrides = listOf(
            Override.Renamed("name", from = "fullName"),
            Override.Const("source", "String", 1),
            Override.Const("age", "Long", 2),
            Override.Const("nickname", "String", 3),
        )

        lines(overrides) shouldBe listOf(
            "UserDto.nickname — withFieldConst names 'nickname', which is not a constructor parameter of UserDto.",
        )
    }

    @Test
    fun `a field overridden twice names both overrides`() {
        val overrides = listOf(
            Override.Renamed("name", from = "fullName"),
            Override.Const("name", "String", 1),
            Override.Const("source", "String", 2),
            Override.Const("age", "Long", 3),
        )

        lines(overrides) shouldBe
            listOf("UserDto.name — overridden twice, by withFieldRenamed and withFieldConst. Keep one.")
    }

    @Test
    fun `a value whose type the field cannot hold is refused, since the compiler widens it to fit`() {
        val overrides = listOf(
            Override.Renamed("name", from = "fullName"),
            Override.Const("source", "String", 1),
            Override.Computed("age", "String", 2),
        )

        lines(overrides) shouldBe listOf("UserDto.age: Long — withFieldComputed gives String, which is not a Long.")
    }

    @Test
    fun `a renamed source is transformed into the field, and fails like any other pair`() {
        val overrides = listOf(
            Override.Renamed("name", from = "fullName"),
            Override.Const("source", "String", 1),
            Override.Renamed("age", from = "born"),
        )

        lines(overrides) shouldBe listOf("UserDto.age: Long — no rule transforms Year into Long.")
    }

    @Test
    fun `a renamed source kimney cannot read is named`() {
        val overrides = listOf(
            Override.Renamed("name", from = "inherited"),
            Override.Const("source", "String", 1),
            Override.Const("age", "Long", 2),
        )

        lines(overrides) shouldBe listOf(
            "UserDto.name: String — withFieldRenamed reads User.inherited, which kimney cannot read: " +
                "it is inherited, an extension or not public.",
        )
    }

    @Test
    fun `a missing top-level field suggests the override, a nested one does not`() {
        val nested = FakeModel(
            constructions = mapOf(
                "UserDto" to primary(param("email", "String"), param("address", "AddressDto")),
                "AddressDto" to primary(param("zip", "String")),
            ),
            properties = mapOf("User" to mapOf("address" to "Address")),
        )

        val failed = derive(nested, "User", "UserDto").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.map { it.line } shouldBe listOf(
            "UserDto.email: String — User has no property 'email'. Add it to User, give UserDto.email a default " +
                "value, or add .withFieldConst(UserDto::email, …).",
            "UserDto.address.zip: String — Address has no property 'zip'. Add it to Address, or give AddressDto.zip " +
                "a default value.",
        )
    }

    @Test
    fun `an override chain that escapes its expression has a message of its own`() {
        Failure.OverrideNotStatic(Path("Into<User, UserDto>"), "UserDto").line shouldBe
            "Into<User, UserDto> — the overrides must be one chain from into() to .transform(), written as a " +
            "single expression with property references like UserDto::name and a lambda for withFieldComputed."
    }
}
