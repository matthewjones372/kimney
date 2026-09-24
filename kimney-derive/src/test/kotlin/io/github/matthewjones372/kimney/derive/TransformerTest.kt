package io.github.matthewjones372.kimney.derive

import io.github.matthewjones372.kimney.derive.Container.Kind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class TransformerTest {

    private val model = FakeModel(
        constructions = mapOf(
            "TeamDto" to primary(param("lead", "UserDto"), param("members", "List<UserDto>")),
            "UserDto" to primary(param("name", "String")),
            "Named" to primary(param("name", "String")),
        ),
        properties = mapOf(
            "Team" to mapOf("lead" to "User", "members" to "List<User>"),
            "User" to mapOf("fullName" to "String"),
            "Maybe" to mapOf("name" to "String?"),
        ),
        supertypes = mapOf("User" to setOf("Person"), "UserDto" to setOf("PersonDto")),
        containers = mapOf(
            "List<User>" to Container(Kind.LIST, "User"),
            "List<UserDto>" to Container(Kind.LIST, "UserDto"),
        ),
        sealed = mapOf("Event" to listOf("Event.Joined"), "EventDto" to listOf("EventDto.Joined")),
    )

    private val userToDto = Supplied("User", "UserDto", index = 0)

    @Test
    fun `a transformer fills every pair it fits below the root, in fields and in elements`() {
        derive(model, "Team", "TeamDto", transformers = listOf(userToDto)) shouldBe Derived.Planned(
            Plan.Construct(
                "TeamDto",
                listOf(
                    Arg.FromProperty("lead", "lead", Plan.Transformed(0, "UserDto")),
                    Arg.FromProperty(
                        "members",
                        "members",
                        Plan.Elements(Kind.LIST, "List<UserDto>", Plan.Transformed(0, "UserDto")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a transformer fills a sealed case`() {
        val joined = Supplied("Event.Joined", "EventDto.Joined", 0)

        derive(model, "Event", "EventDto", transformers = listOf(joined)) shouldBe Derived.Planned(
            Plan.SealedByName(
                "EventDto",
                listOf(Arm("Event.Joined", "EventDto.Joined", Plan.Transformed(0, "EventDto.Joined"))),
            ),
        )
    }

    @Test
    fun `a transformer is how a null gets a value`() {
        derive(model, "Maybe", "Named", transformers = listOf(Supplied("String?", "String", 0))) shouldBe
            Derived.Planned(
                Plan.Construct("Named", listOf(Arg.FromProperty("name", "name", Plan.Transformed(0, "String")))),
            )
    }

    @Test
    fun `a transformer fits as a function would, taking a supertype and giving a subtype`() {
        val derived = derive(model, "Team", "TeamDto", transformers = listOf(Supplied("Person", "UserDto", 2)))

        derived.shouldBeInstanceOf<Derived.Planned<String>>().plan.transformersUsed() shouldBe setOf(2)
    }

    @Test
    fun `the root is the chain's own, so a transformer for it is not used`() {
        derive(model, "User", "UserDto", transformers = listOf(userToDto)).shouldBeInstanceOf<Derived.Failed>()
    }

    @Test
    fun `two transformers that fit are refused, naming both`() {
        val both = listOf(userToDto, Supplied("Person", "UserDto", 2))
        val failed = derive(model, "Team", "TeamDto", transformers = both).shouldBeInstanceOf<Derived.Failed>()

        failed.failures.map { it.line } shouldBe listOf(
            "TeamDto.lead: UserDto — two transformers fit User → UserDto: withTransformer #1 and #3. Pass one.",
            "TeamDto.members[]: UserDto — two transformers fit User → UserDto: withTransformer #1 and #3. Pass one.",
        )
    }

    @Test
    fun `the first failure inside a nested pair offers a transformer for that pair`() {
        val failed = derive(model, "Team", "TeamDto").shouldBeInstanceOf<Derived.Failed>()

        failed.failures.map { it.line } shouldBe listOf(
            "TeamDto.lead.name: String — User has no property 'name'. Add it to User, or give UserDto.name a default " +
                "value. Or map User → UserDto with .withTransformer(Transformer<User, UserDto> { … }).",
            "TeamDto.members[].name: String — User has no property 'name'. Add it to User, or give UserDto.name a " +
                "default value. Or map User → UserDto with .withTransformer(Transformer<User, UserDto> { … }).",
        )
    }

    @Test
    fun `a transformer from context is matched like one from the chain, and named by its parameter`() {
        val money = Supplied("User", "UserDto", index = 1, context = "money")

        derive(model, "Team", "TeamDto", transformers = listOf(money)).shouldBeInstanceOf<Derived.Planned<String>>()
            .plan.transformersUsed() shouldBe setOf(1)

        val failed = derive(model, "Team", "TeamDto", transformers = listOf(userToDto, money))
            .shouldBeInstanceOf<Derived.Failed>()
        failed.failures.first().line shouldBe "TeamDto.lead: UserDto — two transformers fit User → UserDto: " +
            "withTransformer #1 and context parameter 'money'. Pass one."
    }
}
