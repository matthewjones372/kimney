// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

sealed interface Box<out T> {
    data class Plain<T>(val value: T) : Box<T>
    data class Many<T>(val values: List<T>) : Box<List<T>>
}
sealed interface BoxDto<out T> {
    data class Plain<T>(val value: T) : BoxDto<T>
    data class Many<T>(val values: List<T>) : BoxDto<List<T>>
}

// Many<T> : Box<List<T>> has a parameter only inside another type, so the hierarchy is not modelled.
fun unsolvable(box: Box<Int>): BoxDto<Int> = <!KIMNEY_CANNOT_TRANSFORM!>box.transformInto<BoxDto<Int>>()<!>
