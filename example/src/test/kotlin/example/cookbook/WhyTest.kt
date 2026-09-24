package example.cookbook

import example.cookbook.why.Plan
import example.cookbook.why.PlanDto
import example.cookbook.why.Signup
import example.cookbook.why.toDto
import example.cookbook.why.toDtoByHand
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The claim docs/why.md opens with, run. */
class WhyTest {

    private val consenting = Signup("ada@example.com", marketingConsent = true)

    @Test
    fun `the hand-written mapper compiles and silently drops the consent`() {
        consenting.toDtoByHand().marketingConsent shouldBe false
    }

    @Test
    fun `the derived mapping carries it, because it is derived again on every build`() {
        consenting.toDto().marketingConsent shouldBe true
    }

    @Test
    fun `the hand-written when sends a new enum entry to the else, and nothing says so`() {
        Plan.ENTERPRISE.toDtoByHand() shouldBe PlanDto.UNKNOWN
    }
}
