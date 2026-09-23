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

    /** The target object, with nothing read from the source. */
    data class ObjectInstance<T>(val target: T) : Plan<T>

    /** Each source entry to the target entry of the same name. */
    data class EnumByName<T>(val source: T, val target: T, val entries: List<String>) : Plan<T>

    /** [plan] on the non-null source; null stays null. */
    data class NullSafe<T>(val plan: Plan<T>) : Plan<T>

    /** [plan]'s result, wrapped in the value class [target]. */
    data class Wrap<T>(val target: T, val plan: Plan<T>) : Plan<T>

    /** The value class [source]'s [property], transformed by [plan]. */
    data class Unwrap<T>(val source: T, val property: String, val plan: Plan<T>) : Plan<T>

    /** Each element through [plan], in source order, into a new [target] built as [kind] builds. */
    data class Elements<T>(val kind: Container.Kind, val target: T, val plan: Plan<T>) : Plan<T>

    /** Each entry's key through [key] and value through [value], in source order. */
    data class Entries<T>(val target: T, val key: Plan<T>, val value: Plan<T>) : Plan<T>

    /** Each source case to the target case of the same name, in source order. */
    data class SealedByName<T>(val target: T, val arms: List<Arm<T>>) : Plan<T>
}

data class Arm<T>(val source: T, val target: T, val plan: Plan<T>)

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
