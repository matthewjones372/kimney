package io.github.matthewjones372.kimney.derive

/**
 * The whole of kimney's decision about one call: a plan to build [target] from [source], or every reason not.
 * [overrides] name top-level fields of [target] only.
 */
fun <T> derive(model: TypeModel<T>, source: T, target: T, overrides: List<Override<T>> = emptyList()): Derived<T> =
    Derivation(model).pair(Site(source, target, Path(model.render(target)), emptyList()), overrides)

/** One pair being derived, where it sits, and the pairs above it. */
private data class Site<T>(val source: T, val target: T, val path: Path, val seen: List<Pair<T, T>>) {
    fun below(field: String, source: T, target: T): Site<T> =
        Site(source, target, path / field, seen + (this.source to this.target))
}

/** One constructor argument, or the failures that stop it; never both. */
private typealias Step<T> = Pair<Arg<T>?, List<Failure>>

private class Derivation<T>(private val model: TypeModel<T>) {

    fun pair(site: Site<T>, overrides: List<Override<T>> = emptyList()): Derived<T> = when {
        model.isSubtypeOf(site.source, site.target) -> Derived.Planned(Plan.Identity)

        site.seen.any { (s, t) -> same(s, site.source) && same(t, site.target) } ->
            failed(Failure.Recursive(site.path, model.render(site.target), model.render(site.source)))

        else -> construct(site, overrides)
    }

    private fun construct(site: Site<T>, overrides: List<Override<T>>): Derived<T> {
        val target = model.render(site.target)
        return when (val construction = model.construction(site.target)) {
            is Construction.Primary -> primary(site, construction.params, overrides)

            is Construction.NotPublic ->
                failed(Failure.NoPrimaryConstructor(site.path, target, "it is ${construction.visibility}"))

            Construction.SecondaryOnly ->
                failed(Failure.NoPrimaryConstructor(site.path, target, "it has only secondary constructors"))

            Construction.NotAClass -> failed(Failure.NoRuleFor(site.path, target, model.render(site.source)))
        }
    }

    private fun primary(site: Site<T>, params: List<Param<T>>, overrides: List<Override<T>>): Derived<T> {
        val names = params.map { it.name }.toSet()
        val stray = overrides.filter { it.field !in names }
            .map { Failure.NotAParameter(site.path / it.field, it.method, model.render(site.target)) }
        val duplicated = overrides.groupBy { it.field }.filterValues { it.size > 1 }
        val duplicates = duplicated.map { (field, all) ->
            Failure.DuplicateOverride(site.path / field, all.map { it.method })
        }
        val steps = params.filterNot { it.name in duplicated }.map { param ->
            overrides.firstOrNull { it.field == param.name }
                ?.let { overridden(it, param, site) }
                ?: derived(param, site)
        }
        val failures = stray + duplicates + steps.flatMap { it.second }
        return if (failures.isEmpty()) {
            Derived.Planned(Plan.Construct(site.target, steps.mapNotNull { it.first }))
        } else {
            Derived.Failed(failures)
        }
    }

    private fun overridden(override: Override<T>, param: Param<T>, site: Site<T>): Step<T> {
        val field = site.path / param.name
        return when (override) {
            is Override.Const -> checked(override.valueType, param, field, override.method) {
                Arg.Const(param.name, override.index)
            }

            is Override.Computed -> checked(override.resultType, param, field, override.method) {
                Arg.Computed(param.name, override.index)
            }

            is Override.Renamed -> model.property(site.source, override.from)
                ?.let { fromProperty(param, override.from, site.below(param.name, it, param.type)) }
                ?: unreadable(field, param, site, override.from)
        }
    }

    private fun unreadable(field: Path, param: Param<T>, site: Site<T>, property: String): Step<T> =
        null to listOf(Failure.UnreadableSource(field, render(param), model.render(site.source), property))

    // The compiler widens an override's type argument until the value fits, so the field's type is checked here.
    private fun checked(given: T, param: Param<T>, field: Path, method: String, arg: () -> Arg<T>): Step<T> =
        if (model.isSubtypeOf(given, param.type)) {
            arg() to emptyList()
        } else {
            null to listOf(Failure.OverrideTypeMismatch(field, render(param), method, model.render(given)))
        }

    private fun derived(param: Param<T>, site: Site<T>): Step<T> {
        val property = model.property(site.source, param.name)
        return when {
            property != null -> fromProperty(param, param.name, site.below(param.name, property, param.type))

            param.hasDefault -> Arg.Default(param.name) to emptyList()

            else -> null to listOf(
                Failure.MissingSource(
                    site.path / param.name,
                    render(param),
                    model.render(site.source),
                    model.render(site.target),
                ),
            )
        }
    }

    private fun fromProperty(param: Param<T>, property: String, nested: Site<T>): Step<T> =
        when (val derived = pair(nested)) {
            is Derived.Planned -> Arg.FromProperty(param.name, property, derived.plan) to emptyList()
            is Derived.Failed -> null to derived.failures
        }

    private fun render(param: Param<T>): String = model.render(param.type)

    private fun same(a: T, b: T): Boolean = model.isSubtypeOf(a, b) && model.isSubtypeOf(b, a)

    private fun failed(failure: Failure): Derived<Nothing> = Derived.Failed(listOf(failure))
}
