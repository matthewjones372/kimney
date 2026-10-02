package io.github.matthewjones372.kimney.derive

/** One constructor argument, or the failures that stop it; never both. */
private typealias Step<T> = Pair<Arg<T>?, List<Failure>>

/**
 * The constructor rule: each parameter from an override, a same-named source property, or its default. [pair]
 * derives what a property holds, so every other rule applies inside a constructed class.
 */
internal class ConstructorRule<T>(
    private val model: TypeModel<T>,
    private val partial: Boolean,
    private val pair: (Site<T>, List<Override<T>>) -> Derived<T>,
) {

    fun construct(site: Site<T>, overrides: List<Override<T>>): Derived<T> {
        val target = model.render(site.target)
        val derived = when (val construction = model.construction(site.target)) {
            is Construction.Primary -> primary(site, construction.params, overrides)

            is Construction.NotPublic ->
                failed(Failure.NoPrimaryConstructor(site.path, target, "it is ${construction.visibility}"))

            Construction.SecondaryOnly ->
                failed(Failure.NoPrimaryConstructor(site.path, target, "it has only secondary constructors"))

            // An override reaching past a leaf names a field it has none of, which says more than "no rule".
            Construction.NotAClass ->
                if (overrides.isEmpty()) {
                    model.noRule(site)
                } else {
                    Derived.Failed(overrides.map { Failure.NotAParameter(site.path / it.field, it.method, target) })
                }
        }
        // Offered only where a class was being built: a leaf pair such as Int into Long has no inside to fix. Not
        // where overrides reach in, since a transformer for the pair would conflict with them.
        val nestedClass = site.path.fields.isNotEmpty() && model.construction(site.target) is Construction.Primary
        return if (derived is Derived.Failed && nestedClass && overrides.isEmpty()) {
            model.offerTransformer(site, derived)
        } else {
            derived
        }
    }

    private fun primary(site: Site<T>, params: List<Param<T>>, overrides: List<Override<T>>): Derived<T> {
        val names = params.map { it.name }.toSet()
        val stray = overrides.filter { it.field !in names }
            .map { Failure.NotAParameter(site.path / it.field, it.method, model.render(site.target)) }
        val (here, deeper) = overrides.partition { it.rest.isEmpty() }
        // A field filled whole cannot also be reached into, so either way it counts as overridden twice.
        val duplicated = here.groupBy { it.field }
            .mapValues { (field, all) -> all + deeper.filter { it.field == field } }
            .filterValues { it.size > 1 }
        val duplicates = duplicated.map { (field, all) ->
            Failure.DuplicateOverride(site.path / field, all.map { it.method })
        }
        val steps = params.filterNot { it.name in duplicated }.map { param ->
            here.firstOrNull { it.field == param.name }
                ?.let { overridden(it, param, site) }
                ?: derived(param, site, deeper.filter { it.field == param.name }.map { it.descend() })
        }
        val failures = stray + duplicates + steps.flatMap { it.second }
        return if (failures.isEmpty()) {
            Derived.Planned(
                Plan.Construct(
                    site.target,
                    steps.mapNotNull {
                        it.first
                    },
                    guardedAt = site.path.toString().takeIf { partial },
                ),
            )
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
                ?.let { fromProperty(param, override.from, beneath(site, param, it, override.from)) }
                ?: unreadable(field, param, site, override.from)
        }
    }

    // The compiler widens an override's type argument until the value fits, so the field's type is checked here.
    private fun checked(given: T, param: Param<T>, field: Path, method: String, arg: () -> Arg<T>): Step<T> =
        if (model.isSubtypeOf(given, param.type)) {
            arg() to emptyList()
        } else {
            null to listOf(Failure.OverrideTypeMismatch(field, model.render(param.type), method, model.render(given)))
        }

    /** [below] are the overrides that reach inside this parameter; a default cannot take them, so it is not used. */
    private fun derived(param: Param<T>, site: Site<T>, below: List<Override<T>> = emptyList()): Step<T> {
        val property = model.property(site.source, param.name)
        return when {
            property != null -> fromProperty(param, param.name, beneath(site, param, property, param.name), below)

            param.hasDefault && below.isEmpty() -> Arg.Default(param.name) to emptyList()

            else -> null to listOf(
                Failure.MissingSource(
                    site.path / param.name,
                    model.render(param.type),
                    model.render(site.source),
                    model.render(site.target),
                ),
            )
        }
    }

    private fun fromProperty(
        param: Param<T>,
        property: String,
        nested: Site<T>,
        below: List<Override<T>> = emptyList(),
    ): Step<T> =
        when (val derived = pair(nested, below)) {
            is Derived.Planned -> Arg.FromProperty(param.name, property, derived.plan) to emptyList()
            is Derived.Failed -> null to derived.failures
        }

    private fun beneath(site: Site<T>, param: Param<T>, property: T, name: String): Site<T> = site.below(
        param.name,
        property,
        param.type,
        owner = model.render(site.target),
        origin = "${model.render(site.source)}.$name",
    )

    private fun unreadable(field: Path, param: Param<T>, site: Site<T>, property: String): Step<T> =
        null to listOf(Failure.UnreadableSource(field, model.render(param.type), model.render(site.source), property))
}
