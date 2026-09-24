package io.github.matthewjones372.kimney.derive

/**
 * The whole of kimney's decision about one call: a plan to build [target] from [source], or every reason not.
 * [overrides] name top-level fields of [target] only; [transformers] serve every pair below the root they fit.
 * [partial] plans a transformation that may fail at runtime, collecting errors instead of refusing to compile.
 * [enums] and [sealed] serve every pair of their enums and sealed types, the root included.
 */
fun <T> derive(
    model: TypeModel<T>,
    source: T,
    target: T,
    overrides: List<Override<T>> = emptyList(),
    transformers: List<Supplied<T>> = emptyList(),
    partial: Boolean = false,
    enums: List<EnumOverride<T>> = emptyList(),
    sealed: List<SealedOverride<T>> = emptyList(),
): Derived<T> {
    // A root with overrides is not a pair recursion may return to: its overrides name its own fields only.
    val root = Site(source, target, Path(model.render(target)), emptyList(), shareable = overrides.isEmpty())
    return Derivation(model, transformers, partial, enums, sealed).pair(root, overrides)
}

/**
 * A user's transformer from [source] to [target]. [index] is unique within one derivation; [context] names the
 * context parameter it came from, and is null for one passed to the chain at that index.
 */
data class Supplied<T>(
    val source: T,
    val target: T,
    val index: Int,
    val context: String? = null,
    /** A `PartialTransformer`: it serves partial transformations only, and its errors are re-rooted. */
    val canFail: Boolean = false,
)

/**
 * One pair being derived, where it sits, and the pairs above it. [owner] is the class the field at [path] belongs
 * to and [origin] the source property it was read from, as a message names them; both are null at the root.
 */
internal data class Site<T>(
    val source: T,
    val target: T,
    val path: Path,
    val seen: List<Pair<T, T>?>,
    val owner: String? = null,
    val origin: String? = null,
    val shareable: Boolean = true,
) {
    /** Where this pair sits on the path: a reference to it back from below names it by this. */
    val depth: Int get() = seen.size

    fun below(field: String, source: T, target: T, owner: String? = null, origin: String? = null): Site<T> =
        Site(source, target, path / field, seen + (this.source to this.target).takeIf { shareable }, owner, origin)
}

private class Derivation<T>(
    val model: TypeModel<T>,
    private val transformers: List<Supplied<T>>,
    private val partial: Boolean,
    private val enums: List<EnumOverride<T>>,
    private val sealed: List<SealedOverride<T>>,
) {
    private val constructors = ConstructorRule(model, partial) { pair(it) }

    // With overrides the target is built, even from its own type: `into<_, User>()` is a copy with changes.
    fun pair(site: Site<T>, overrides: List<Override<T>> = emptyList()): Derived<T> =
        supplied(site) ?: unsupplied(site, overrides)

    /** The transformer that fits [site], or why none can be used; null when none fits. */
    private fun supplied(site: Site<T>): Derived<T>? {
        val fits = model.fitting(site, transformers)
        // A transformer that can fail serves a partial transformation only; in a total one it is named, not used.
        val fitting = if (partial) fits else fits.filterNot { it.canFail }
        val onlyFallible = !partial && fitting.isEmpty() && fits.isNotEmpty()
        return when {
            onlyFallible ->
                failed(Failure.FallibleInTotal(site.path, model.render(site.target), model.render(site.source)))

            fitting.size == 1 -> Derived.Planned(
                Plan.Transformed(
                    fitting.single().index,
                    site.target,
                    relocateAt = site.path.toString().takeIf { fitting.single().canFail },
                ),
            )

            fitting.size > 1 -> failed(
                Failure.AmbiguousTransformer(
                    site.path,
                    model.render(site.target),
                    model.render(site.source),
                    fitting.filter { it.context == null }.map { it.index },
                    fitting.mapNotNull { it.context },
                ),
            )

            else -> null
        }
    }

    private fun unsupplied(site: Site<T>, overrides: List<Override<T>>): Derived<T> {
        val above = site.seen.indexOfFirst {
            it != null && model.same(it.first, site.source) &&
                model.same(it.second, site.target)
        }
        return when {
            model.passes(site, overrides) && !mapped(site) -> Derived.Planned(Plan.Identity)
            above >= 0 -> Derived.Planned(Plan.Reference(above))
            else -> named(site, byShape(site, overrides))
        }
    }

    // An enum or sealed link on the pair changes what its entries or cases become: even a type into itself is mapped.
    private fun mapped(site: Site<T>): Boolean {
        val cases = model.sealedCases(site.target).orEmpty()
        val caseTargets = sealed.map {
            when (it) {
                is SealedOverride.Renamed -> it.target
                is SealedOverride.Fallback -> it.target
            }
        }
        return enums.any { model.same(it.target, site.target) } ||
            caseTargets.any { target -> cases.any { model.same(it.type, target) } }
    }

    /** A plan that refers back to its own pair becomes named, so the lowering can give it a function to call. */
    private fun named(site: Site<T>, derived: Derived<T>): Derived<T> =
        if (derived is Derived.Planned && derived.plan.refersTo(site.depth)) {
            Derived.Planned(Plan.Named(site.depth, site.source, site.target, derived.plan))
        } else {
            derived
        }

    /** Every rule but the constructor answers by the target's shape; overrides can only fill a constructor. */
    private fun byShape(site: Site<T>, overrides: List<Override<T>>): Derived<T> {
        val shaped = shapedRule(site) ?: return constructors.construct(site, overrides)
        return if (overrides.isEmpty()) {
            shaped()
        } else {
            Derived.Failed(
                overrides.map { Failure.NotAParameter(site.path / it.field, it.method, model.render(site.target)) },
            )
        }
    }

    private fun shapedRule(site: Site<T>): (() -> Derived<T>)? {
        val enumEntries = model.enumEntries(site.target)
        val cases = model.sealedCases(site.target)
        return when {
            model.isNullable(site.target) -> { -> model.nullable(site, ::pair) }

            model.isNullable(site.source) -> { ->
                if (partial) model.required(site, ::pair) else model.nullToNonNull(site)
            }

            model.valueClass(site.target) != null -> { -> model.wrap(site, partial, ::pair) }

            model.valueClass(site.source) != null -> { -> model.unwrap(site, ::pair) }

            model.container(site.target) != null -> { -> model.containers(site, ::pair) }

            model.isObject(site.target) -> { ->
                if (model.isObject(site.source)) {
                    Derived.Planned(Plan.ObjectInstance(site.target))
                } else {
                    model.noRule(site)
                }
            }

            enumEntries != null -> { ->
                model.enumEntries(site.source)?.let { model.enumByName(site, it, enumEntries, enums) }
                    ?: model.noRule(site)
            }

            cases != null -> { ->
                model.sealedCases(site.source)?.let { model.sealedByName(site, it, sealed, ::supplied, ::pair) }
                    ?: model.caseIntoSealed(site, cases, ::pair)
            }

            else -> null
        }
    }
}

internal fun failed(failure: Failure): Derived<Nothing> = Derived.Failed(listOf(failure))

internal fun <T> TypeModel<T>.noRule(site: Site<T>): Derived<T> =
    failed(Failure.NoRuleFor(site.path, render(site.target), render(site.source)))

internal fun <T> TypeModel<T>.same(a: T, b: T): Boolean = isSubtypeOf(a, b) && isSubtypeOf(b, a)

/** The first failure inside a nested pair offers a transformer for that pair, unless one inside it already did. */
internal fun <T> TypeModel<T>.offerTransformer(site: Site<T>, failed: Derived.Failed): Derived.Failed {
    val first = failed.failures.first()
    return if (first is Failure.WithTransformerHint) {
        failed
    } else {
        val hinted = Failure.WithTransformerHint(first, render(site.source), render(site.target))
        Derived.Failed(listOf(hinted) + failed.failures.drop(1))
    }
}

// The root is the chain's own pair, so a transformer serves only what lies below it.
private fun <T> TypeModel<T>.fitting(site: Site<T>, transformers: List<Supplied<T>>): List<Supplied<T>> =
    if (site.path.fields.isEmpty()) {
        emptyList()
    } else {
        transformers.filter { isSubtypeOf(site.source, it.source) && isSubtypeOf(it.target, site.target) }
    }

private fun <T> TypeModel<T>.mutableContainer(type: T): Boolean =
    container(type)?.kind?.let { it != it.readOnly } == true

// A mutable target is always a new collection: handing over the source's own would let one side's edits appear in the
// other. With overrides, the target is rebuilt even from its own type.
private fun <T> TypeModel<T>.passes(site: Site<T>, overrides: List<Override<T>>): Boolean =
    overrides.isEmpty() && isSubtypeOf(site.source, site.target) && !mutableContainer(site.target)
