package io.github.matthewjones372.kimney.derive

sealed interface Derived<out T> {
    data class Planned<T>(val plan: Plan<T>) : Derived<T>

    data class Failed(val failures: List<Failure>) : Derived<Nothing> {
        init {
            require(failures.isNotEmpty()) { "a failed derivation names at least one failure" }
        }

        fun message(source: String, target: String): String =
            "Cannot transform $source → $target:\n" + failures.joinToString("\n") { "    ${it.line}" }
    }
}

sealed interface Plan<out T> {
    /** The source value already is a target value. */
    data object Identity : Plan<Nothing>

    data class Construct<T>(val target: T, val args: List<Arg<T>>) : Plan<T>
}

sealed interface Arg<out T> {
    val param: String

    /** The source's [property], transformed by [plan]. */
    data class FromProperty<T>(override val param: String, val property: String, val plan: Plan<T>) : Arg<T>

    /** The value given by the override at [index] in the chain. */
    data class Const(override val param: String, val index: Int) : Arg<Nothing>

    /** The lambda at [index] in the chain, applied to the source. */
    data class Computed(override val param: String, val index: Int) : Arg<Nothing>

    /** Left out of the call, so the parameter's default applies. */
    data class Default(override val param: String) : Arg<Nothing>
}
