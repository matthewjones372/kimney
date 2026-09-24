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

/**
 * A link naming enum entries. Unlike an [Override] it names no field: it serves every pair of its enums wherever the
 * pair occurs, the root included. [index] is its place in the chain.
 */
sealed interface EnumOverride<out T> {
    val target: T
    val index: Int

    /** The [source] entry [from] becomes the [target] entry [to], whatever the names. */
    data class Renamed<T>(
        val source: T,
        val from: String,
        override val target: T,
        val to: String,
        override val index: Int,
    ) : EnumOverride<T>

    /** Every entry with nothing else to become, from any enum, becomes the [target] entry [to]. */
    data class Fallback<T>(override val target: T, val to: String, override val index: Int) : EnumOverride<T>
}
