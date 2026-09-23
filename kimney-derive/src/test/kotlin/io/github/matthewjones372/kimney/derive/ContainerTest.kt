package io.github.matthewjones372.kimney.derive

import io.github.matthewjones372.kimney.derive.Container.Kind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ContainerTest {

    private val model = FakeModel(
        constructions = mapOf(
            "LineDto" to primary(param("sku", "String")),
            "OrderDto" to primary(param("tags", "List<TagDto>")),
        ),
        properties = mapOf(
            "Line" to mapOf("sku" to "String"), "Bad" to emptyMap(),
            "Order" to mapOf("tags" to "Set<Tag>"),
        ),
        enums = mapOf("Tag" to listOf("A"), "TagDto" to listOf("A")),
        valueClasses = mapOf("UserId" to param("raw", "Long")),
        containers = mapOf(
            "List<Line>" to Container(Kind.LIST, "Line"),
            "List<Bad>" to Container(Kind.LIST, "Bad"),
            "List<LineDto>" to Container(Kind.LIST, "LineDto"),
            "Iterable<LineDto>" to Container(Kind.ITERABLE, "LineDto"),
            "Set<Tag>" to Container(Kind.SET, "Tag"),
            "Set<TagDto>" to Container(Kind.SET, "TagDto"),
            "List<TagDto>" to Container(Kind.LIST, "TagDto"),
            "Array<Line>" to Container(Kind.ARRAY, "Line"),
            "Array<LineDto>" to Container(Kind.ARRAY, "LineDto"),
            "Map<UserId, Line>" to Container(Kind.MAP, "Line", key = "UserId"),
            "Map<Long, LineDto>" to Container(Kind.MAP, "LineDto", key = "Long"),
            "Map<Line, Tag>" to Container(Kind.MAP, "Tag", key = "Line"),
            "Map<LineDto, TagDto>" to Container(Kind.MAP, "TagDto", key = "LineDto"),
        ),
    )

    private val line = Plan.Construct("LineDto", listOf(Arg.FromProperty("sku", "sku", Plan.Identity)))

    private fun lines(source: String, target: String): List<String> =
        derive(model, source, target).shouldBeInstanceOf<Derived.Failed>().failures.map { it.line }

    @Test
    fun `each element goes through every rule, into the same kind or a read-only supertype`() {
        derive(model, "List<Line>", "List<LineDto>") shouldBe
            Derived.Planned(Plan.Elements(Kind.LIST, "List<LineDto>", line))
        derive(model, "List<Line>", "Iterable<LineDto>") shouldBe
            Derived.Planned(Plan.Elements(Kind.LIST, "Iterable<LineDto>", line))
        derive(model, "Array<Line>", "Array<LineDto>") shouldBe
            Derived.Planned(Plan.Elements(Kind.ARRAY, "Array<LineDto>", line))
    }

    @Test
    fun `a failure inside an element has the element in its path`() {
        lines("List<Bad>", "List<LineDto>") shouldBe listOf(
            "List<LineDto>[].sku: String — Bad has no property 'sku'. Add it to Bad, or give LineDto.sku a default " +
                "value. Or map Bad → LineDto with .withTransformer(Transformer<Bad, LineDto> { … }).",
        )
    }

    @Test
    fun `a crossing of kinds is refused with the reason, and the override on a top-level field`() {
        lines("Order", "OrderDto") shouldBe listOf(
            "OrderDto.tags: List<TagDto> — a Set is not turned into a List. " +
                "Fill it with .withFieldComputed(OrderDto::tags) { … }.",
        )
    }

    @Test
    fun `a map transforms its values, and its keys through a value class`() {
        derive(model, "Map<UserId, Line>", "Map<Long, LineDto>") shouldBe Derived.Planned(
            Plan.Entries("Map<Long, LineDto>", Plan.Unwrap("UserId", "raw", Plan.Identity), line),
        )
    }

    @Test
    fun `a map key that could collide is refused`() {
        lines("Map<Line, Tag>", "Map<LineDto, TagDto>") shouldBe listOf(
            "Map<LineDto, TagDto>[key]: LineDto — keys are transformed only as themselves or through a value " +
                "class, since Line into LineDto could turn two keys into one.",
        )
    }
}
