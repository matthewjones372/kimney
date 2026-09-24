package io.github.matthewjones372.kimney.derive

/**
 * The whole of kimney's decision about one call: a plan to build [target] from [source], or every reason not.
 * [overrides] name top-level fields of [target] only; [transformers] serve every pair below the root they fit.
 */
fun <T> derive(
    model: TypeModel<T>,
    source: T,
    target: T,
    overrides: List<Override<T>> = emptyList(),
    transformers: List<Supplied<T>> = emptyList(),
): Derived<T> {
    val root = Site(source, target, Path(model.render(target)), emptyList())
    return Derivation(model, transformers).pair(root, overrides)
}

/**
 * A user's transformer from [source] to [target]. [index] is unique within one derivation; [context] names the
 * context parameter it came from, and is null for one passed to the chain at that index.
 */
data class Supplied<T>(val source: T, val target: T, val index: Int, val context: String? = null)

/**
 * One pair being derived, where it sits, and the pairs above it. [owner] is the class the field at [path] belongs
 * to and [origin] the source property it was read from, as a message names them; both are null at the root.
 */
internal data class Site<T>(
    val source: T,
    val target: T,
    val path: Path,
    val seen: List<Pair<T, T>>,
    val owner: String? = null,
    val origin: String? = null,
) {
    fun below(field: String, source: T, target: T, owner: String? = null, origin: String? = null): Site<T> =
        Site(source, target, path / field, seen + (this.source to this.target), owner, origin)
}

private class Derivation<T>(val model: TypeModel<T>, private val transformers: List<Supplied<T>>) {
    private val constructors = ConstructorRule(model) { pair(it) }

    // With overrides the target is built, even from its own type: `into<_, User>()` is a copy with changes.
    fun pair(site: Site<T>, overrides: List<Override<T>> = emptyList()): Derived<T> {
        val fitting = model.fitting(site, transformers)
        return when {
            fitting.size == 1 -> Derived.Planned(Plan.Transformed(fitting.single().index, site.target))

            fitting.size > 1 -> failed(
                Failure.AmbiguousTransformer(
                    site.path,
                    model.render(site.target),
                    model.render(site.source),
                    fitting.filter { it.context == null }.map { it.index },
                    fitting.mapNotNull { it.context },
                ),
            )

            else -> unsupplied(site, overrides)
        }
    }

    private fun unsupplied(site: Site<T>, overrides: List<Override<T>>): Derived<T> = when {
        overrides.isEmpty() && model.isSubtypeOf(site.source, site.target) -> Derived.Planned(Plan.Identity)

        site.seen.any { (s, t) -> model.same(s, site.source) && model.same(t, site.target) } ->
            failed(Failure.Recursive(site.path, model.render(site.target), model.render(site.source)))

        else -> byShape(site, overrides)
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

            model.isNullable(site.source) -> { -> model.nullToNonNull(site) }

            model.valueClass(site.target) != null -> { -> model.wrap(site, ::pair) }

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
                model.enumEntries(site.source)?.let { model.enumByName(site, it, enumEntries) }
                    ?: model.noRule(site)
            }

            cases != null -> { ->
                model.sealedCases(site.source)?.let { model.sealedByName(site, it, cases, ::pair) }
                    ?: model.noRule(site)
            }

            else -> null
        }
    }
}

internal fun failed(failure: Failure): Derived<Nothing> = Derived.Failed(listOf(failure))

internal fun <T> TypeModel<T>.noRule(site: Site<T>): Derived<T> =
    failed(Failure.NoRuleFor(site.path, render(site.target), render(site.source)))

private fun <T> TypeModel<T>.same(a: T, b: T): Boolean = isSubtypeOf(a, b) && isSubtypeOf(b, a)

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
