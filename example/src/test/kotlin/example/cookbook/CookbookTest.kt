package example.cookbook

import example.cookbook.collections.toDto
import example.cookbook.copy.suspend
import example.cookbook.crossing.toDto
import example.cookbook.enums.toDto
import example.cookbook.first.toDto
import example.cookbook.ids.toOwnerId
import example.cookbook.ids.toRow
import example.cookbook.ids.toUser
import example.cookbook.nested.toDto
import example.cookbook.optionals.toDto
import example.cookbook.overrides.toDto
import example.cookbook.sealed.toDto
import example.cookbook.transformers.toDto
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Each recipe in docs/cookbook.md, run: the page claims what these assert. */
class CookbookTest {

    @Test
    fun `the first transform drops what the target does not ask for`() {
        example.cookbook.first.User("Ada", "ada@example.com", admin = true).toDto() shouldBe
            example.cookbook.first.UserDto("Ada", "ada@example.com")
    }

    @Test
    fun `nested classes are derived, and defaults fill what the source lacks`() {
        val customer = example.cookbook.nested.Customer("Ada", example.cookbook.nested.Address("1 Loop Rd", "N1"))

        customer.toDto() shouldBe example.cookbook.nested.CustomerDto(
            "Ada",
            example.cookbook.nested.AddressDto("1 Loop Rd", "N1", "GB"),
            "standard",
        )
    }

    @Test
    fun `overrides rename, compute and fix fields`() {
        example.cookbook.overrides.Person("Ada Lovelace", 1815, "ada@example.com").toDto(year = 2026) shouldBe
            example.cookbook.overrides.PersonDto("Ada Lovelace", 211, "ada@example.com", "import")
    }

    @Test
    fun `a copy with changes rebuilds the value`() {
        example.cookbook.copy.Account(7, "ada", suspended = false).suspend() shouldBe
            example.cookbook.copy.Account(7, "ada", suspended = true)
    }

    @Test
    fun `enums map by name`() {
        example.cookbook.enums.Status.SUSPENDED.toDto() shouldBe example.cookbook.enums.StatusDto.SUSPENDED
    }

    @Test
    fun `sealed cases map by name, each with every rule`() {
        example.cookbook.sealed.Payment.Card("4242").toDto() shouldBe
            example.cookbook.sealed.PaymentDto.Card("4242", "unknown")
        example.cookbook.sealed.Payment.Cash.toDto() shouldBe example.cookbook.sealed.PaymentDto.Cash
    }

    @Test
    fun `optionals stay optional, and null stays null`() {
        example.cookbook.optionals.Profile("ada", billing = null).toDto() shouldBe
            example.cookbook.optionals.ProfileDto("ada", billing = null)
    }

    @Test
    fun `value classes unwrap and wrap`() {
        val user = example.cookbook.ids.User(example.cookbook.ids.UserId(7), "Ada")

        user.toRow() shouldBe example.cookbook.ids.UserRow(7, "Ada")
        example.cookbook.ids.UserRow(7, "Ada").toUser() shouldBe user
        user.id.toOwnerId() shouldBe example.cookbook.ids.OwnerId(7)
    }

    @Test
    fun `collections transform element by element, keeping order`() {
        val sku = example.cookbook.collections.Sku("A-1")
        val order = example.cookbook.collections.Order(
            lines = listOf(example.cookbook.collections.Line(sku, quantity = 2)),
            tags = linkedSetOf(example.cookbook.collections.Tag.FRAGILE, example.cookbook.collections.Tag.GIFT),
            stock = mapOf(sku to 5),
        )

        val dto = order.toDto()

        dto.lines shouldBe listOf(example.cookbook.collections.LineDto("A-1", 2))
        dto.tags.toList() shouldBe
            listOf(example.cookbook.collections.TagDto.FRAGILE, example.cookbook.collections.TagDto.GIFT)
        dto.stock shouldBe mapOf("A-1" to 5)
    }

    @Test
    fun `crossing kinds is a decision the recipe writes down`() {
        example.cookbook.crossing.Article("t", linkedSetOf("b", "a")).toDto() shouldBe
            example.cookbook.crossing.ArticleDto("t", listOf("a", "b"))
    }

    @Test
    fun `a transformer serves every nested pair it fits, in fields and elements`() {
        val ada = example.cookbook.transformers.User("Ada Lovelace", "ada@example.com")

        example.cookbook.transformers.Team(ada, listOf(ada), motto = null).toDto() shouldBe
            example.cookbook.transformers.TeamDto(
                example.cookbook.transformers.UserDto("Ada Lovelace", "ada@example.com"),
                listOf(example.cookbook.transformers.UserDto("Ada Lovelace", "ada@example.com")),
                motto = "(none)",
            )
    }
}
