// MODULE: lib
// FILE: lib.kt
package lib

sealed interface Outcome<out T> {
    data class Done<T>(val value: T) : Outcome<T>
    data object Pending : Outcome<Nothing>
}
sealed interface OutcomeView<out T> {
    data class Done<T>(val value: T) : OutcomeView<T>
    data object Pending : OutcomeView<Nothing>
}

// MODULE: main(lib)
// FILE: main.kt
import io.github.matthewjones372.kimney.transformInto
import lib.Outcome
import lib.OutcomeView

fun box(): String {
    val done: Outcome<Int> = Outcome.Done(3)
    val pending: Outcome<Int> = Outcome.Pending
    val mapped = listOf(done, pending).map { it.transformInto<OutcomeView<Int>>() }
    return if (mapped == listOf(OutcomeView.Done(3), OutcomeView.Pending)) "OK" else "Fail: $mapped"
}
