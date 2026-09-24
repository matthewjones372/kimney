import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.PartialError
import io.github.matthewjones372.kimney.transformIntoPartial

@JvmInline value class Quantity(val units: Int) {
    init {
        require(units > 0) { "quantity must be positive" }
    }
}

data class LineRequest(val sku: String?, val quantity: Int)
data class Request(val customer: Long?, val lines: List<LineRequest>)

var built = 0

data class Line(val sku: String, val quantity: Quantity) {
    init {
        built++
    }
}
data class Command(val customer: Long, val lines: List<Line>)

fun box(): String {
    val bad = Request(null, listOf(LineRequest("A-1", 2), LineRequest(null, 1), LineRequest("C-3", 0)))
        .transformIntoPartial<Command>()
    val expectedErrors = Partial.Errors(
        listOf(
            PartialError("Command.customer", "is null"),
            PartialError("Command.lines[].sku", "is null"),
            PartialError("Command.lines[].quantity", "quantity must be positive"),
        ),
    )
    val builtByBad = built
    val good = Request(7, listOf(LineRequest("A-1", 2))).transformIntoPartial<Command>()
    return when {
        bad != expectedErrors -> "Fail errors: $bad"
        builtByBad != 1 -> "Fail: a Line was built from a failed part ($builtByBad built)"
        good != Partial.Ok(Command(7, listOf(Line("A-1", Quantity(2))))) -> "Fail ok: $good"
        else -> "OK"
    }
}
