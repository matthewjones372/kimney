package io.github.matthewjones372.kimney.derive

/** Nullable and value class rules: each derives through the type inside, so every other rule applies there. */
internal fun <T> TypeModel<T>.nullable(site: Site<T>, pair: (Site<T>) -> Derived<T>): Derived<T> {
    val target = nonNull(site.target)
    return if (isNullable(site.source)) {
        pair(site.copy(source = nonNull(site.source), target = target)).map { Plan.NullSafe(it) }
    } else {
        pair(site.copy(target = target))
    }
}

internal fun <T> TypeModel<T>.nullToNonNull(site: Site<T>): Derived<T> = Derived.Failed(
    listOf(Failure.NullableToNonNull(site.path, render(site.target), render(site.source), site.owner, site.origin)),
)

internal fun <T> TypeModel<T>.wrap(site: Site<T>, pair: (Site<T>) -> Derived<T>): Derived<T> {
    val inner = checkNotNull(valueClass(site.target)) { "wrap is tried only for a value class target" }
    // The held property is a segment of the path, so a failure inside reads `Label.text`, not the bare inner type.
    val below = site.below(inner.name, site.source, inner.type, owner = render(site.target), origin = site.origin)
    return pair(below).map { Plan.Wrap(site.target, it) }
}

internal fun <T> TypeModel<T>.unwrap(site: Site<T>, pair: (Site<T>) -> Derived<T>): Derived<T> {
    val inner = checkNotNull(valueClass(site.source)) { "unwrap is tried only for a value class source" }
    return pair(site.copy(source = inner.type)).map { Plan.Unwrap(site.source, inner.name, it) }
}

private fun <T> Derived<T>.map(f: (Plan<T>) -> Plan<T>): Derived<T> = when (this) {
    is Derived.Planned -> Derived.Planned(f(plan))
    is Derived.Failed -> this
}
