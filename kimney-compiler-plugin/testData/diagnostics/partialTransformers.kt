// RUN_PIPELINE_TILL: FRONTEND
import dev.surefold.kimney.Partial
import dev.surefold.kimney.PartialError
import dev.surefold.kimney.PartialTransformer
import dev.surefold.kimney.into

data class Form(val checkIn: String)
data class Booking(val checkIn: Int)

val parse = PartialTransformer<String, Int> { it.toIntOrNull()?.let { n -> Partial.Ok(n) } ?: Partial.Errors(listOf(PartialError("", "is not a number"))) }
val unrelated = PartialTransformer<Long, Int> { Partial.Ok(it.toInt()) }

fun total(form: Form): Booking = <!KIMNEY_CANNOT_TRANSFORM!>form.into<_, Booking>().withPartialTransformer(parse).transform()<!>

fun unused(form: Form): Partial<Booking> =
    <!KIMNEY_UNUSED_TRANSFORMER!>form.into<_, Booking>().withPartialTransformer(parse).withPartialTransformer(unrelated)<!>.transformPartial()
