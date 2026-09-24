import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.PartialError
import io.github.matthewjones372.kimney.PartialTransformer
import io.github.matthewjones372.kimney.into
import io.github.matthewjones372.kimney.transformIntoPartial

@JvmInline value class Day(val iso: String)

val isoDay = PartialTransformer<String, Day> { text ->
    if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)) Partial.Ok(Day(text)) else Partial.Errors(listOf(PartialError("", "is not a date")))
}

data class GuestForm(val name: String?)
data class Guest(val name: String)
val guest = PartialTransformer<GuestForm, Guest> { it.transformIntoPartial<Guest>() }

data class BookingForm(val checkIn: String, val lead: GuestForm)
data class Booking(val checkIn: Day, val lead: Guest)

context(days: PartialTransformer<String, Day>)
fun inContext(form: BookingForm): Partial<Booking> =
    form.into<_, Booking>().withPartialTransformer(guest).transformPartial()

fun box(): String {
    val bad = BookingForm("tomorrow", GuestForm(null)).into<_, Booking>()
        .withPartialTransformer(isoDay)
        .withPartialTransformer(guest)
        .transformPartial()
    val expected = Partial.Errors(listOf(PartialError("Booking.checkIn", "is not a date"), PartialError("Booking.lead.name", "is null")))
    val good = context(isoDay) { inContext(BookingForm("2026-09-24", GuestForm("Ada"))) }
    return when {
        bad != expected -> "Fail bad: $bad"
        good != Partial.Ok(Booking(Day("2026-09-24"), Guest("Ada"))) -> "Fail good: $good"
        else -> "OK"
    }
}
