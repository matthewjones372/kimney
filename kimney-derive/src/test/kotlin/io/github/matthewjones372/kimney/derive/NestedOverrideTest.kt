package io.github.matthewjones372.kimney.derive

import io.github.matthewjones372.kimney.derive.Container.Kind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class NestedOverrideTest {

    private val model = FakeModel(
        constructions = mapOf(
            "PersonDto" to primary(
                param("address", "AddressDto"),
                param("billing", "AddressDto?"),
                param("lines", "List<LineDto>"),
                param("tag", "Tag"),
                param("note", "NoteDto", hasDefault = true),
            ),
            "StrictDto" to primary(param("billing", "AddressDto")),
            "AddressDto" to primary(param("zip", "String"), param("geo", "GeoDto")),
            "GeoDto" to primary(param("country", "String")),
            "LineDto" to primary(param("sku", "String")),
            "Tag" to primary(param("label", "String")),
            "NoteDto" to primary(param("text", "String")),
        ),
        properties = mapOf(
            "Person" to mapOf("address" to "Address", "billing" to "Address?", "lines" to "List<Line>", "tag" to "Tag"),
            "Address" to mapOf("zip" to "String", "geo" to "Geo"),
            "Geo" to mapOf("country" to "String"),
            "Line" to mapOf("sku" to "String"),
            "Tag" to mapOf("label" to "String"),
        ),
        containers = mapOf(
            "List<Line>" to Container(Kind.LIST, "Line"),
            "List<LineDto>" to Container(Kind.LIST, "LineDto"),
        ),
    )

    private fun derived(vararg overrides: Override<String>, transformers: List<Supplied<String>> = emptyList()) =
        derive(model, "Person", "PersonDto", overrides.toList(), transformers)

    private fun planned(vararg overrides: Override<String>): Plan.Construct<String> =
        derived(*overrides).shouldBeInstanceOf<Derived.Planned<String>>().plan.shouldBeInstanceOf()

    private fun lines(vararg overrides: Override<String>, transformers: List<Supplied<String>> = emptyList()) =
        derived(*overrides, transformers = transformers).shouldBeInstanceOf<Derived.Failed>().failures.map { it.line }

    /** The argument at [path], through constructors and null checks. */
    private fun Plan<String>.arg(vararg path: String): Arg<String> = path.drop(1).fold(argHere(path.first())) {
            arg,
            name,
        ->
        arg.shouldBeInstanceOf<Arg.FromProperty<String>>().plan.argHere(name)
    }

    private fun Plan<String>.argHere(name: String): Arg<String> =
        (if (this is Plan.NullSafe) plan else this).shouldBeInstanceOf<Plan.Construct<String>>()
            .args.single { it.param == name }

    private fun Plan<String>.at(name: String): Plan<String> =
        arg(name).shouldBeInstanceOf<Arg.FromProperty<String>>().plan

    @Test
    fun `an override one field down fills that field and derives the rest of the pair`() {
        val plan = planned(Override.Const("address", "String", 0, rest = listOf("zip")))

        plan.arg("address", "zip") shouldBe Arg.Const("zip", 0)
        plan.arg("address", "geo").shouldBeInstanceOf<Arg.FromProperty<String>>()
    }

    @Test
    fun `an override reaches as deep as its path, and a computed one keeps its index`() {
        val plan = planned(Override.Computed("address", "String", 3, rest = listOf("geo", "country")))

        plan.arg("address", "geo", "country") shouldBe Arg.Computed("country", 3)
    }

    @Test
    fun `a pair of the same type is rebuilt when an override reaches into it`() {
        planned().arg("tag") shouldBe Arg.FromProperty("tag", "tag", Plan.Identity)

        val tag = planned(Override.Const("tag", "String", 0, rest = listOf("label"))).at("tag")

        tag shouldBe Plan.Construct("Tag", listOf(Arg.Const("label", 0)))
    }

    @Test
    fun `an override passes through a nullable step, applying behind the null check`() {
        val billing = planned(Override.Const("billing", "String", 0, rest = listOf("zip"))).at("billing")

        billing.shouldBeInstanceOf<Plan.NullSafe<String>>().plan.shouldBeInstanceOf<Plan.Construct<String>>()
            .args.first() shouldBe Arg.Const("zip", 0)
    }

    @Test
    fun `a field below that is not a constructor parameter is named with its whole path`() {
        lines(Override.Const("address", "String", 0, rest = listOf("postcode"))) shouldBe listOf(
            "PersonDto.address.postcode — withFieldConst names 'postcode', which is not a constructor parameter " +
                "of AddressDto.",
        )
    }

    @Test
    fun `a path through a container is refused once, pointing at a transformer for the element`() {
        lines(
            Override.Const("lines", "String", 0, rest = listOf("sku")),
            Override.Computed("lines", "String", 1, rest = listOf("sku")),
        ) shouldBe listOf(
            "PersonDto.lines — the selector crosses List<LineDto>; an override cannot reach inside elements. " +
                "Map LineDto with .withTransformer(…).",
        )
    }

    @Test
    fun `an override inside a pair a transformer maps names both`() {
        lines(
            Override.Const("address", "String", 0, rest = listOf("zip")),
            transformers = listOf(Supplied("Address", "AddressDto", index = 1)),
        ) shouldBe listOf(
            "PersonDto.address.zip — withFieldConst reaches inside Address → AddressDto, which withTransformer #2 " +
                "also maps. Keep one.",
        )
    }

    @Test
    fun `a field filled whole and reached into is overridden twice`() {
        lines(
            Override.Const("address", "AddressDto", 0),
            Override.Const("address", "String", 1, rest = listOf("zip")),
        ) shouldBe listOf("PersonDto.address — overridden twice, by withFieldConst and withFieldConst. Keep one.")
    }

    @Test
    fun `a default does not stand in for a field an override reaches into`() {
        lines(Override.Const("note", "String", 0, rest = listOf("text"))).single() shouldBe
            "PersonDto.note: NoteDto — Person has no property 'note'. Add it to Person, give PersonDto.note a " +
            "default value, or add .withFieldConst(PersonDto::note, …)."
    }

    @Test
    fun `a nullable source into a non-null field still fails as null, override or not`() {
        val override = Override.Const("billing", "String", 0, rest = listOf("zip"))

        derive(model, "Person", "StrictDto", listOf(override)).shouldBeInstanceOf<Derived.Failed>()
            .failures.single().shouldBeInstanceOf<Failure.NullableToNonNull>()
    }

    @Test
    fun `a path past a leaf names the field the leaf does not have`() {
        lines(Override.Const("address", "Int", 0, rest = listOf("zip", "length"))) shouldBe listOf(
            "PersonDto.address.zip.length — withFieldConst names 'length', which is not a constructor parameter " +
                "of String.",
        )
    }
}
