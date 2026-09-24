package io.github.matthewjones372.kimney

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class PartialTest {

    private val errors = Partial.Errors(
        listOf(
            PartialError("", "is not a date"),
            PartialError("Booking.checkIn", "is null"),
            PartialError("Booking", "is not valid"),
        ),
    )

    @Test
    fun `relocating puts a bare error at the new path and replaces a rooted one's root`() {
        errors.relocatedTo("Trip.outbound") shouldBe listOf(
            PartialError("Trip.outbound", "is not a date"),
            PartialError("Trip.outbound.checkIn", "is null"),
            PartialError("Trip.outbound", "is not valid"),
        )
    }

    @Test
    fun `withPartialTransformer says it was not replaced`() {
        val chain = Into<String, Int>()

        shouldThrow<KimneyNotApplied> {
            chain.withPartialTransformer(PartialTransformer<String, Int> { Partial.Ok(1) })
        }
            .message shouldStartWith "withPartialTransformer reached runtime"
    }
}
