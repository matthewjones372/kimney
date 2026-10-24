package io.github.matthewjones372.kimney.derive

/**
 * One override from the chain. It names the target field [field] and, when it reaches deeper, the fields [rest] below
 * it: `{ it.address.zip }` is `address` then `zip`. [index] is its place in the chain.
 */
sealed interface Override<out T> {
    val field: String
    val rest: List<String>
    val method: String

    /** The same override, one pair down: what [field] holds is where it now applies. */
    fun descend(): Override<T>

    data class Const<T>(
        override val field: String,
        val valueType: T,
        val index: Int,
        override val rest: List<String> = emptyList(),
    ) : Override<T> {
        override val method get() = "withFieldConst"

        override fun descend() = copy(field = rest.first(), rest = rest.drop(1))
    }

    data class Computed<T>(
        override val field: String,
        val resultType: T,
        val index: Int,
        override val rest: List<String> = emptyList(),
    ) : Override<T> {
        override val method get() = "withFieldComputed"

        override fun descend() = copy(field = rest.first(), rest = rest.drop(1))
    }

    /** Top-level only: its source is a property of the root source. */
    data class Renamed(override val field: String, val from: String) : Override<Nothing> {
        override val rest: List<String> get() = emptyList()
        override val method get() = "withFieldRenamed"

        override fun descend() = error("withFieldRenamed names a top-level field")
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

/**
 * A link naming sealed cases by class. As an [EnumOverride] does, it serves every pair of its sealed types, the root
 * included.
 */
sealed interface SealedOverride<out T> {
    val index: Int

    /** The case [source] becomes the target case [target], derived by every rule, whatever the names. */
    data class Renamed<T>(val source: T, val target: T, override val index: Int) : SealedOverride<T>

    /** Every case with nothing else to become, from any sealed type, becomes the object case [target]. */
    data class Fallback<T>(val target: T, override val index: Int) : SealedOverride<T>
}
