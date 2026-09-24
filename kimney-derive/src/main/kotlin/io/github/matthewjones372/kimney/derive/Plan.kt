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

    /** [guardedAt] is set in a partial transformation: an `IllegalArgumentException` it throws is an error there. */
    data class Construct<T>(val target: T, val args: List<Arg<T>>, val guardedAt: String? = null) : Plan<T>

    /** In a partial transformation: a null source is an error at [path]; a value goes on through [plan]. */
    data class Required<T>(val path: String, val plan: Plan<T>) : Plan<T>

    /** [plan], given a name by the [depth] of its pair on the path, so a [Reference] below can call it again. */
    data class Named<T>(val depth: Int, val source: T, val target: T, val plan: Plan<T>) : Plan<T>

    /** The enclosing [Named] plan with this [depth], applied again: the pair has met itself. */
    data class Reference(val depth: Int) : Plan<Nothing>

    /**
     * The user's transformer at [index] in the chain, applied to the source. [relocateAt] is set for one that can
     * fail: its errors are re-rooted there.
     */
    data class Transformed<T>(val index: Int, val target: T, val relocateAt: String? = null) : Plan<T>

    /** The target object, with nothing read from the source. */
    data class ObjectInstance<T>(val target: T) : Plan<T>

    /** Each source entry to the target entry of the same name. */

    /**
     * A `when` over [source]'s entries by identity, each [arms] entry naming the [target] entry it becomes; an entry
     * compiled in after this call becomes [otherwise], or fails if there is none. [uses] are the chain links that
     * chose an arm, so an unused one can be named.
     */
    data class EnumByName<T>(
        val source: T,
        val target: T,
        val arms: List<EnumArm>,
        val otherwise: String? = null,
        val uses: Set<Int> = emptySet(),
    ) : Plan<T>

    /** [plan] on the non-null source; null stays null. */
    data class NullSafe<T>(val plan: Plan<T>) : Plan<T>

    /** [plan]'s result, wrapped in the value class [target]. */
    data class Wrap<T>(val target: T, val plan: Plan<T>, val guardedAt: String? = null) : Plan<T>

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

/** The source entry [from] becomes the target entry [to]. */
data class EnumArm(val from: String, val to: String)

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

/** The chain indices of every transformer this plan calls and enum link it follows, so an unused one can be named. */
fun <T> Plan<T>.linksUsed(): Set<Int> = when (this) {
    is Plan.Transformed -> setOf(index)
    is Plan.EnumByName -> uses
    Plan.Identity, is Plan.ObjectInstance, is Plan.Reference -> emptySet()
    is Plan.Named -> plan.linksUsed()
    is Plan.Construct -> args.flatMap { (it as? Arg.FromProperty)?.plan?.linksUsed().orEmpty() }.toSet()
    is Plan.NullSafe -> plan.linksUsed()
    is Plan.Required -> plan.linksUsed()
    is Plan.Wrap -> plan.linksUsed()
    is Plan.Unwrap -> plan.linksUsed()
    is Plan.Elements -> plan.linksUsed()
    is Plan.Entries -> key.linksUsed() + value.linksUsed()
    is Plan.SealedByName -> arms.flatMap { it.plan.linksUsed() }.toSet()
}

/** Whether a [Plan.Reference] to [depth] occurs outside any [Plan.Named] that already binds it. */
internal fun <T> Plan<T>.refersTo(depth: Int): Boolean = when (this) {
    is Plan.Reference -> this.depth == depth
    is Plan.Named -> this.depth != depth && plan.refersTo(depth)
    Plan.Identity, is Plan.ObjectInstance, is Plan.EnumByName, is Plan.Transformed -> false
    is Plan.Construct -> args.any { (it as? Arg.FromProperty)?.plan?.refersTo(depth) == true }
    is Plan.NullSafe -> plan.refersTo(depth)
    is Plan.Required -> plan.refersTo(depth)
    is Plan.Wrap -> plan.refersTo(depth)
    is Plan.Unwrap -> plan.refersTo(depth)
    is Plan.Elements -> plan.refersTo(depth)
    is Plan.Entries -> key.refersTo(depth) || value.refersTo(depth)
    is Plan.SealedByName -> arms.any { it.plan.refersTo(depth) }
}
