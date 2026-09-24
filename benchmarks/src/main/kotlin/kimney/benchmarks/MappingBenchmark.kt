package kimney.benchmarks

import io.github.matthewjones372.kimney.Partial
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Each mapping twice: as kimney derives it and as it is written by hand.
 *
 * The inputs are fields of the state, so the JIT cannot fold them into
 * constants, and every result is returned, so it cannot drop the work.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
open class MappingBenchmark {
    private val order = Order(
        OrderId(42),
        CustomerId(7),
        List(LINES) { OrderLine(Sku("SKU-$it"), Quantity(it + 1)) },
        OrderStatus.SHIPPED,
    )

    private val request = PlaceOrderRequest(7, List(LINES) { LineRequest("SKU-$it", it + 1) })

    private val customer = Customer(
        CustomerId(7),
        "Ada",
        Address("1 High St", "London", "N1 1AA"),
        Address("2 Low Rd", "Leeds", "LS1 1AA"),
    )

    private val validForm = SignupForm("ada@example.com", "Ada", List(LINES) { "friend$it@example.com" })

    private val invalidForm = SignupForm(null, "Ada", List(LINES) { if (it % 2 == 0) "friend$it" else null })

    @Benchmark open fun orderViewKimney(): OrderView = order.toViewKimney()

    @Benchmark open fun orderViewByHand(): OrderView = order.toViewByHand()

    @Benchmark open fun placeOrderKimney(): PlaceOrder = request.toCommandKimney()

    @Benchmark open fun placeOrderByHand(): PlaceOrder = request.toCommandByHand()

    @Benchmark open fun customerKimney(): CustomerDto = customer.toDtoKimney()

    @Benchmark open fun customerByHand(): CustomerDto = customer.toDtoByHand()

    @Benchmark open fun signupValidKimney(): Partial<Signup> = validForm.validateKimney()

    @Benchmark open fun signupValidByHand(): Partial<Signup> = validForm.validateByHand()

    @Benchmark open fun signupInvalidKimney(): Partial<Signup> = invalidForm.validateKimney()

    @Benchmark open fun signupInvalidByHand(): Partial<Signup> = invalidForm.validateByHand()

    private companion object {
        const val LINES = 10
    }
}
