package io.github.matthewjones372.kimney.derive

/** The whole of kimney's decision about one call: a plan to build [target] from [source], or every reason not. */
fun <T> derive(model: TypeModel<T>, source: T, target: T): Derived<T> =
    Derivation(model).pair(source, target, Path(model.render(target)), seen = emptyList())

private class Derivation<T>(private val model: TypeModel<T>) {

    fun pair(source: T, target: T, path: Path, seen: List<Pair<T, T>>): Derived<T> = when {
        model.isSubtypeOf(source, target) -> Derived.Planned(Plan.Identity)

        seen.any { (s, t) -> same(s, source) && same(t, target) } ->
            failed(Failure.Recursive(path, model.render(target), model.render(source)))

        else -> construct(source, target, path, seen + (source to target))
    }

    private fun construct(source: T, target: T, path: Path, seen: List<Pair<T, T>>): Derived<T> =
        when (val construction = model.construction(target)) {
            is Construction.Primary -> {
                val results = construction.params.map { arg(it, source, target, path, seen) }
                val failures = results.flatMap { it.second }
                if (failures.isEmpty()) {
                    Derived.Planned(Plan.Construct(target, results.mapNotNull { it.first }))
                } else {
                    Derived.Failed(failures)
                }
            }

            is Construction.NotPublic ->
                failed(Failure.NoPrimaryConstructor(path, model.render(target), "it is ${construction.visibility}"))

            Construction.SecondaryOnly ->
                failed(Failure.NoPrimaryConstructor(path, model.render(target), "it has only secondary constructors"))

            Construction.NotAClass -> failed(Failure.NoRuleFor(path, model.render(target), model.render(source)))
        }

    /** One constructor argument, or the failures that stop it; never both. */
    private fun arg(
        param: Param<T>,
        source: T,
        target: T,
        path: Path,
        seen: List<Pair<T, T>>,
    ): Pair<Arg<T>?, List<Failure>> {
        val field = path / param.name
        val property = model.property(source, param.name)
        return when {
            property != null -> when (val nested = pair(property, param.type, field, seen)) {
                is Derived.Planned -> Arg.FromProperty(param.name, nested.plan) to emptyList()
                is Derived.Failed -> null to nested.failures
            }

            param.hasDefault -> Arg.Default(param.name) to emptyList()

            else -> null to listOf(
                Failure.MissingSource(field, model.render(param.type), model.render(source), model.render(target)),
            )
        }
    }

    private fun same(a: T, b: T): Boolean = model.isSubtypeOf(a, b) && model.isSubtypeOf(b, a)

    private fun failed(failure: Failure): Derived<Nothing> = Derived.Failed(listOf(failure))
}
