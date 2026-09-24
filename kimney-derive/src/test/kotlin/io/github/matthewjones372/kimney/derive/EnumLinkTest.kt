package io.github.matthewjones372.kimney.derive

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class EnumLinkTest {

    private val model = FakeModel(
        constructions = mapOf("AccountDto" to primary(param("status", "StatusDto"))),
        properties = mapOf("Account" to mapOf("status" to "Status")),
        enums = mapOf(
            "Status" to listOf("ACTIVE", "PENDING", "ARCHIVED"),
            "Wire" to listOf("ACTIVE", "LEGACY"),
            "StatusDto" to listOf("ACTIVE", "PENDING", "INACTIVE", "UNKNOWN"),
        ),
        containers = mapOf(
            "List<Status>" to Container(Container.Kind.LIST, "Status"),
            "List<StatusDto>" to Container(Container.Kind.LIST, "StatusDto"),
        ),
    )

    private fun rename(from: String, to: String, index: Int = 0, source: String = "Status") =
        EnumOverride.Renamed(source, from, "StatusDto", to, index)

    private fun fallback(to: String, index: Int = 0) = EnumOverride.Fallback("StatusDto", to, index)

    private fun arms(vararg pairs: Pair<String, String>) = pairs.map { (from, to) -> EnumArm(from, to) }

    @Test
    fun `a rename sends its entry to the one named, and the rest still match by name`() {
        derive(model, "Status", "StatusDto", enums = listOf(rename("ARCHIVED", "INACTIVE"))) shouldBe
            Derived.Planned(
                Plan.EnumByName(
                    "Status",
                    "StatusDto",
                    arms("ACTIVE" to "ACTIVE", "PENDING" to "PENDING", "ARCHIVED" to "INACTIVE"),
                    uses = setOf(0),
                ),
            )
    }

    @Test
    fun `a rename wins over a name match`() {
        val links = listOf(rename("PENDING", "ACTIVE"), rename("ARCHIVED", "INACTIVE", index = 1))

        derive(model, "Status", "StatusDto", enums = links) shouldBe Derived.Planned(
            Plan.EnumByName(
                "Status",
                "StatusDto",
                arms("ACTIVE" to "ACTIVE", "PENDING" to "ACTIVE", "ARCHIVED" to "INACTIVE"),
                uses = setOf(0, 1),
            ),
        )
    }

    @Test
    fun `a fallback takes what nothing else matched, and is the else`() {
        derive(model, "Wire", "StatusDto", enums = listOf(fallback("UNKNOWN"))) shouldBe Derived.Planned(
            Plan.EnumByName(
                "Wire",
                "StatusDto",
                arms("ACTIVE" to "ACTIVE", "LEGACY" to "UNKNOWN"),
                "UNKNOWN",
                setOf(0),
            ),
        )
    }

    @Test
    fun `a rename and a fallback together, the rename first`() {
        val links = listOf(rename("ARCHIVED", "INACTIVE"), fallback("UNKNOWN", index = 1))

        derive(model, "Status", "StatusDto", enums = links) shouldBe Derived.Planned(
            Plan.EnumByName(
                "Status",
                "StatusDto",
                arms("ACTIVE" to "ACTIVE", "PENDING" to "PENDING", "ARCHIVED" to "INACTIVE"),
                "UNKNOWN",
                setOf(0, 1),
            ),
        )
    }

    @Test
    fun `a fallback with nothing to catch is still used`() {
        derive(model, "Status", "StatusDto", enums = listOf(fallback("UNKNOWN"), rename("ARCHIVED", "INACTIVE", 1)))
            .shouldBeInstanceOf<Derived.Planned<String>>().plan.linksUsed() shouldBe setOf(0, 1)
    }

    @Test
    fun `links serve a nested pair and every element`() {
        val links = listOf(rename("ARCHIVED", "INACTIVE"))

        derive(model, "Account", "AccountDto", enums = links)
            .shouldBeInstanceOf<Derived.Planned<String>>().plan.linksUsed() shouldBe setOf(0)
        derive(model, "List<Status>", "List<StatusDto>", enums = links)
            .shouldBeInstanceOf<Derived.Planned<String>>().plan.linksUsed() shouldBe setOf(0)
    }

    @Test
    fun `a rename of another pair is not used here`() {
        val other = listOf(rename("LEGACY", "INACTIVE", source = "Wire"))
        val derived = derive(model, "Status", "StatusDto", enums = other).shouldBeInstanceOf<Derived.Failed>()

        derived.failures.map { it.path.toString() } shouldBe listOf("StatusDto")
    }

    @Test
    fun `an enum into itself is mapped when a link names it`() {
        val self = EnumOverride.Renamed("Status", "ARCHIVED", "Status", "PENDING", 0)

        derive(model, "Status", "Status", enums = listOf(self)) shouldBe Derived.Planned(
            Plan.EnumByName(
                "Status",
                "Status",
                arms("ACTIVE" to "ACTIVE", "PENDING" to "PENDING", "ARCHIVED" to "PENDING"),
                uses = setOf(0),
            ),
        )
        derive(model, "Status", "Status") shouldBe Derived.Planned(Plan.Identity)
    }

    @Test
    fun `two renames of one entry and two fallbacks are named, by their places in the chain`() {
        val links = listOf(
            rename("ARCHIVED", "INACTIVE"),
            fallback("UNKNOWN", index = 1),
            rename("ARCHIVED", "UNKNOWN", index = 2),
            fallback("INACTIVE", index = 3),
        )

        derive(model, "Status", "StatusDto", enums = links).shouldBeInstanceOf<Derived.Failed>()
            .failures.map { it.line } shouldBe listOf(
            "StatusDto — Status.ARCHIVED is renamed twice, by withEnumEntryRenamed #1 and #3. Keep one.",
            "StatusDto — StatusDto falls back twice, by withEnumFallback #2 and #4. Keep one.",
        )
    }
}
