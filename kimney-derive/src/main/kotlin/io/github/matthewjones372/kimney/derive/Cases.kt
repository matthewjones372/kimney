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
    val target = render(site.target)
    val duplicates = renames.groupBy { it.from }.filterValues { it.size > 1 }.map { (entry, links) ->
        val what = "${render(site.source)}.$entry is renamed"
        Failure.DuplicateLink(site.path, target, what, "withEnumEntryRenamed", links.map { it.index })
    } + listOfNotNull(
        fallbacks.takeIf { it.size > 1 }?.let { links ->
            Failure.DuplicateLink(site.path, target, "$target falls back", "withEnumFallback", links.map { it.index })
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

/**
 * Each case pair goes back through [pair], so every rule applies inside a case. A case is renamed first, then matched
 * by name; with neither, a transformer from it into the target parent builds it ([supplied]), or the fallback does.
 */
internal fun <T> TypeModel<T>.sealedByName(
    site: Site<T>,
    from: List<Case<T>>,
    links: List<SealedOverride<T>>,
    supplied: (Site<T>) -> Derived<T>?,
    pair: (Site<T>) -> Derived<T>,
): Derived<T> {
    val to = sealedCases(site.target).orEmpty()
    val renames = links.filterIsInstance<SealedOverride.Renamed<T>>()
        .filter { link -> from.any { same(it.type, link.source) } && to.any { same(it.type, link.target) } }
    val fallbacks = links.filterIsInstance<SealedOverride.Fallback<T>>()
        .filter { link -> isObject(link.target) && to.any { same(it.type, link.target) } }
    val duplicates = duplicateCases(site, renames, fallbacks)
    if (duplicates.isNotEmpty()) return Derived.Failed(duplicates)
    val fallback = fallbacks.singleOrNull()?.target
    val arms = from.map { case ->
        val match = renames.firstOrNull { same(it.source, case.type) }?.target
            ?: to.firstOrNull { it.name == case.name }?.type
        val byHand = if (match == null) supplied(site.below(case.name, case.type, site.target)) else null
        val (built, derived) = when {
            match != null -> match to pair(site.below(case.name, case.type, match))

            byHand != null -> site.target to byHand

            fallback != null -> fallback to Derived.Planned(Plan.ObjectInstance(fallback))

            else ->
                site.target to
                    failed(Failure.MissingCase(site.path, render(site.target), render(case.type), "subclass"))
        }
        when (derived) {
            is Derived.Planned -> Arm(case.type, built, derived.plan) to emptyList()
            is Derived.Failed -> null to derived.failures
        }
    }
    val failures = arms.flatMap { it.second }
    return if (failures.isEmpty()) {
        val uses = (renames.map { it.index } + fallbacks.map { it.index }).toSet()
        Derived.Planned(Plan.SealedByName(site.target, arms.mapNotNull { it.first }, fallback, uses))
    } else {
        Derived.Failed(failures)
    }
}

private fun <T> TypeModel<T>.duplicateCases(
    site: Site<T>,
    renames: List<SealedOverride.Renamed<T>>,
    fallbacks: List<SealedOverride.Fallback<T>>,
): List<Failure> {
    val target = render(site.target)
    val renamed = renames.groupBy { render(it.source) }.filterValues { it.size > 1 }.map { (case, links) ->
        Failure.DuplicateLink(site.path, target, "$case is renamed", "withSealedCaseRenamed", links.map { it.index })
    }
    val fellBack = fallbacks.takeIf { it.size > 1 }?.let { links ->
        Failure.DuplicateLink(site.path, target, "$target falls back", "withSealedFallback", links.map { it.index })
    }
    return renamed + listOfNotNull(fellBack)
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
            listOf(
                Failure.MissingCase(site.path, render(site.target), render(site.source), "subclass", offers = false),
            ),
        )
    return pair(site.copy(target = match.type))
}
