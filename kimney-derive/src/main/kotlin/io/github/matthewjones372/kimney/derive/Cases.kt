package io.github.matthewjones372.kimney.derive

/**
 * Enum and sealed rules: each source case to the target case of the same name, every missing one reported. An enum
 * entry is renamed first, then matched by name, then sent to the fallback.
 */
internal fun <T> TypeModel<T>.enumByName(
    site: Site<T>,
    from: List<String>,
    to: List<String>,
    enums: List<EnumOverride<T>>,
): Derived<T> {
    val renames = enums.filterIsInstance<EnumOverride.Renamed<T>>()
        .filter { same(it.source, site.source) && same(it.target, site.target) }
    val fallbacks = enums.filterIsInstance<EnumOverride.Fallback<T>>().filter { same(it.target, site.target) }
    val duplicates = renames.groupBy { it.from }.filterValues { it.size > 1 }.map { (entry, links) ->
        Failure.DuplicateEnumLink(site.path, render(site.target), "${render(site.source)}.$entry is renamed", links)
    } + listOfNotNull(
        fallbacks.takeIf { it.size > 1 }
            ?.let {
                Failure.DuplicateEnumLink(site.path, render(site.target), "${render(site.target)} falls back", it)
            },
    )
    if (duplicates.isNotEmpty()) return Derived.Failed(duplicates)
    val fallback = fallbacks.singleOrNull()?.to
    val chosen = from.map { entry ->
        entry to (renames.firstOrNull { it.from == entry }?.to ?: entry.takeIf { it in to } ?: fallback)
    }
    val missing = chosen.filter { it.second == null }.map { (entry, _) ->
        Failure.MissingCase(site.path, render(site.target), "${render(site.source)}.$entry", "entry")
    }
    return if (missing.isEmpty()) {
        val arms = chosen.mapNotNull { (entry, target) -> target?.let { EnumArm(entry, it) } }
        val uses = (renames + fallbacks).map { it.index }.toSet()
        Derived.Planned(Plan.EnumByName(site.source, site.target, arms, fallback, uses))
    } else {
        Derived.Failed(missing)
    }
}

/** Each case pair goes back through [pair], so every rule applies inside a case. */
internal fun <T> TypeModel<T>.sealedByName(
    site: Site<T>,
    from: List<Case<T>>,
    to: List<Case<T>>,
    pair: (Site<T>) -> Derived<T>,
): Derived<T> {
    val arms = from.map { case ->
        val match = to.firstOrNull { it.name == case.name }
        if (match == null) {
            null to listOf(Failure.MissingCase(site.path, render(site.target), render(case.type), "subclass"))
        } else {
            when (val derived = pair(site.below(case.name, case.type, match.type))) {
                is Derived.Planned -> Arm(case.type, match.type, derived.plan) to emptyList()
                is Derived.Failed -> null to derived.failures
            }
        }
    }
    val failures = arms.flatMap { it.second }
    return if (failures.isEmpty()) {
        Derived.Planned(Plan.SealedByName(site.target, arms.mapNotNull { it.first }))
    } else {
        Derived.Failed(failures)
    }
}

/** A case, not its sealed parent, into a sealed target: the target's case of the same name, as one arm would be. */
internal fun <T> TypeModel<T>.caseIntoSealed(
    site: Site<T>,
    to: List<Case<T>>,
    pair: (Site<T>) -> Derived<T>,
): Derived<T> {
    val name = caseName(site.source) ?: return noRule(site)
    val match = to.firstOrNull { it.name == name }
        ?: return Derived.Failed(
            listOf(Failure.MissingCase(site.path, render(site.target), render(site.source), "subclass")),
        )
    return pair(site.copy(target = match.type))
}
