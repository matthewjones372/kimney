package io.github.matthewjones372.kimney.derive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class DeriveTest {

    private val users = FakeModel(
        constructions = mapOf(
            "UserDto" to primary(param("name", "String"), param("address", "AddressDto")),
            "AddressDto" to primary(param("street", "String"), param("country", "String", hasDefault = true)),
        ),
        properties = mapOf(
            "User" to mapOf("name" to "String", "address" to "Address", "admin" to "Boolean"),
            "Address" to mapOf("street" to "String"),
        ),
    )

    @Test
    fun `a source that already is the target passes through`() {
        val model = FakeModel(emptyMap(), supertypes = mapOf("Admin" to setOf("User")))

        derive(model, "Admin", "User") shouldBe Derived.Planned(Plan.Identity)
    }

    @Test
    fun `a target is built from same-named properties, recursing, with defaults where the source has nothing`() {
        derive(users, "User", "UserDto") shouldBe Derived.Planned(
            Plan.Construct(
                "UserDto",
                listOf(
                    Arg.FromProperty("name", "name", Plan.Identity),
                    Arg.FromProperty(
                        "address",
                        "address",
                        Plan.Construct(
                            "AddressDto",
                            listOf(Arg.FromProperty("street", "street", Plan.Identity), Arg.Default("country")),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a source property wins over the parameter's default`() {
        val model = FakeModel(
            constructions = mapOf("Dto" to primary(param("country", "String", hasDefault = true))),
            properties = mapOf("Src" to mapOf("country" to "String")),
        )

        derive(model, "Src", "Dto") shouldBe
            Derived.Planned(Plan.Construct("Dto", listOf(Arg.FromProperty("country", "country", Plan.Identity))))
    }

    @Test
    fun `a field with no source and no default is named, with its path and the way to fill it`() {
        val model = FakeModel(
            constructions = mapOf("UserDto" to primary(param("name", "String"), param("email", "String"))),
            properties = mapOf("User" to mapOf("name" to "String")),
        )

        val failed = derive(model, "User", "UserDto").shouldBeInstanceOf<Derived.Failed>()

        failed.message("User", "UserDto") shouldBe """
            Cannot transform User → UserDto:
                UserDto.email: String — User has no property 'email'. Add it to User, give UserDto.email a default value, or add .withFieldConst(UserDto::email, …).
        """.trimIndent()
    }

    @Test
    fun `every failure is reported, nested ones with their full path`() {
        val model = FakeModel(
            constructions = mapOf(
                "UserDto" to primary(param("email", "String"), param("address", "AddressDto")),
                "AddressDto" to primary(param("zip", "String")),
            ),
            properties = mapOf("User" to mapOf("address" to "Address")),
        )

        val failed = derive(model, "User", "UserDto").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.map { it.path.toString() } shouldBe listOf("UserDto.email", "UserDto.address.zip")
        failed.failures[1].line shouldBe "UserDto.address.zip: String — Address has no property 'zip'. " +
            "Add it to Address, or give AddressDto.zip a default value. " +
            "Or map Address → AddressDto with .withTransformer(Transformer<Address, AddressDto> { … })."
    }

    @Test
    fun `a pair with no rule says which types it could not connect`() {
        val model = FakeModel(
            constructions = mapOf("Dto" to primary(param("age", "Long"))),
            properties = mapOf("Src" to mapOf("age" to "Int")),
        )

        val failed = derive(model, "Src", "Dto").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.single().line shouldBe "Dto.age: Long — no rule transforms Int into Long."
    }

    @Test
    fun `a failure at the root has no path of its own`() {
        val failed = derive(FakeModel(emptyMap()), "Int", "String").shouldBeInstanceOf<Derived.Failed>()

        failed.message("Int", "String") shouldBe
            "Cannot transform Int → String:\n    String — no rule transforms Int into String."
    }

    @Test
    fun `a target whose primary constructor is hidden says how it is hidden`() {
        val model = FakeModel(mapOf("Dto" to Construction.NotPublic("private")))

        val failed = derive(model, "Src", "Dto").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.single().line shouldBe "Dto — Dto has no public primary constructor: it is private."
    }

    @Test
    fun `a target with only secondary constructors says so`() {
        val model = FakeModel(mapOf("Dto" to Construction.SecondaryOnly))

        val failed = derive(model, "Src", "Dto").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.single().line shouldBe
            "Dto — Dto has no public primary constructor: it has only secondary constructors."
    }
}
