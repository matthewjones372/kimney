package io.github.matthewjones372.kimney.derive

import io.github.matthewjones372.kimney.derive.Container.Kind

/** The kinds each source kind may become: itself, or a read-only supertype of it. */
private val allowed = mapOf(
    Kind.LIST to setOf(Kind.LIST, Kind.COLLECTION, Kind.ITERABLE),
    Kind.SET to setOf(Kind.SET, Kind.COLLECTION, Kind.ITERABLE),
    Kind.COLLECTION to setOf(Kind.COLLECTION, Kind.ITERABLE),
    Kind.ITERABLE to setOf(Kind.ITERABLE),
    Kind.MAP to setOf(Kind.MAP),
    Kind.ARRAY to setOf(Kind.ARRAY),
)

/** One element at a time, through every rule; a map's keys only where no two can become one. */
internal fun <T> TypeModel<T>.containers(site: Site<T>, pair: (Site<T>) -> Derived<T>): Derived<T> {
    val to = checkNotNull(container(site.target)) { "containers is tried only for a container target" }
    val from = container(site.source)
    val crossing = from != null && to.kind !in allowed.getValue(from.kind)
    return when {
        from == null -> Derived.Failed(listOf(Failure.NoRuleFor(site.path, render(site.target), render(site.source))))

        crossing -> Derived.Failed(
            listOf(Failure.ContainerMismatch(site.path, render(site.target), from.kind, to.kind, site.owner)),
        )

        to.kind == Kind.MAP -> entries(site, from, to, pair)

        else -> element(site, from, to, pair).map { Plan.Elements(from.kind, site.target, it) }
    }
}

private fun <T> TypeModel<T>.entries(
    site: Site<T>,
    from: Container<T>,
    to: Container<T>,
    pair: (Site<T>) -> Derived<T>,
): Derived<T> {
    val sourceKey = checkNotNull(from.key) { "a map has a key" }
    val targetKey = checkNotNull(to.key) { "a map has a key" }
    val keyPath = Site(sourceKey, targetKey, site.path / "[key]", site.seen + (site.source to site.target))
    val key = when (val derived = pair(keyPath)) {
        is Derived.Planned -> if (injective(derived.plan)) {
            derived
        } else {
            Derived.Failed(listOf(Failure.KeyMayCollide(keyPath.path, render(targetKey), render(sourceKey))))
        }

        is Derived.Failed -> derived
    }
    val value = element(site, from, to, pair)
    return when {
        key is Derived.Planned && value is Derived.Planned ->
            Derived.Planned(Plan.Entries(site.target, key.plan, value.plan))

        else -> Derived.Failed(listOfNotNull(key as? Derived.Failed, value as? Derived.Failed).flatMap { it.failures })
    }
}

private fun <T> TypeModel<T>.element(
    site: Site<T>,
    from: Container<T>,
    to: Container<T>,
    pair: (Site<T>) -> Derived<T>,
): Derived<T> = pair(site.below("[]", from.element, to.element, origin = "an element of ${render(site.source)}"))

/** Only these plans are one-to-one, so only these may produce map keys. */
private fun <T> injective(plan: Plan<T>): Boolean = when (plan) {
    Plan.Identity -> true

    is Plan.Wrap -> injective(plan.plan)

    is Plan.Unwrap -> injective(plan.plan)

    // A user's transformer may well send two keys to one.
    is Plan.Transformed, is Plan.Named, is Plan.Reference, is Plan.Construct, is Plan.ObjectInstance,
    is Plan.EnumByName, is Plan.NullSafe,
    is Plan.Elements, is Plan.Entries, is Plan.SealedByName,
    -> false
}
