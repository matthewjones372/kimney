package kimney.benchmarks

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** A benchmark pair only compares like with like if both sides give the same answer. */
class SameAnswerTest {
    private val benchmark = MappingBenchmark()

    @Test
    fun `an order's view`() {
        benchmark.orderViewKimney() shouldBe benchmark.orderViewByHand()
    }

    @Test
    fun `a request's command`() {
        benchmark.placeOrderKimney() shouldBe benchmark.placeOrderByHand()
    }

    @Test
    fun `a customer's dto`() {
        benchmark.customerKimney() shouldBe benchmark.customerByHand()
    }

    @Test
    fun `a valid form`() {
        benchmark.signupValidKimney() shouldBe benchmark.signupValidByHand()
    }

    @Test
    fun `an invalid form, every error with its path`() {
        benchmark.signupInvalidKimney() shouldBe benchmark.signupInvalidByHand()
    }
}
