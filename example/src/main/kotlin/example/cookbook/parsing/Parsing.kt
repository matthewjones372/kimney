package example.cookbook.parsing

import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.PartialError
import io.github.matthewjones372.kimney.PartialTransformer
import io.github.matthewjones372.kimney.into
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A parse that can fail, written once: its error has no path, so it lands wherever the date sits. */
val isoDate = PartialTransformer<String, LocalDate> { text ->
    try {
        Partial.Ok(LocalDate.parse(text))
    } catch (e: DateTimeParseException) {
        Partial.Errors(listOf(PartialError("", "is not a date: ${e.parsedString}")))
    }
}

data class StayForm(val checkIn: String, val checkOut: String)

data class Stay(val checkIn: LocalDate, val checkOut: LocalDate)

fun StayForm.toStay(): Partial<Stay> = into<_, Stay>().withPartialTransformer(isoDate).transformPartial()
