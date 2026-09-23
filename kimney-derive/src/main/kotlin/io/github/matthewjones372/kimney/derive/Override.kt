package io.github.matthewjones372.kimney.derive

/** One override from the chain, naming a top-level field of the target. [index] is its place in the chain. */
sealed interface Override<out T> {
    val field: String
    val method: String

    data class Const<T>(override val field: String, val valueType: T, val index: Int) : Override<T> {
        override val method get() = "withFieldConst"
    }

    data class Computed<T>(override val field: String, val resultType: T, val index: Int) : Override<T> {
        override val method get() = "withFieldComputed"
    }

    data class Renamed(override val field: String, val from: String) : Override<Nothing> {
        override val method get() = "withFieldRenamed"
    }
}
