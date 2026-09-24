package io.github.matthewjones372.kimney.derive

/** Types by name, declared the way a test reads. A trailing `?` makes a type nullable. */
class FakeModel(
    private val constructions: Map<String, Construction<String>>,
    private val properties: Map<String, Map<String, String>> = emptyMap(),
    private val supertypes: Map<String, Set<String>> = emptyMap(),
    private val enums: Map<String, List<String>> = emptyMap(),
    private val sealed: Map<String, List<String>> = emptyMap(),
    private val objects: Set<String> = emptySet(),
    private val valueClasses: Map<String, Param<String>> = emptyMap(),
    private val containers: Map<String, Container<String>> = emptyMap(),
) : TypeModel<String> {
    override fun render(type: String): String = type

    override fun isSubtypeOf(sub: String, sup: String): Boolean = when {
        isNullable(sup) -> isSubtypeOf(nonNull(sub), nonNull(sup))
        isNullable(sub) -> false
        else -> sub == sup || sup in supertypes[sub].orEmpty()
    }

    override fun construction(type: String): Construction<String> = constructions[type] ?: Construction.NotAClass

    override fun property(owner: String, name: String): String? = properties[owner]?.get(name)

    override fun enumEntries(type: String): List<String>? = enums[type]

    /** A case named `Shape.Circle` answers to `Circle`, as a nested subclass does. */
    override fun sealedCases(type: String): List<Case<String>>? =
        sealed[type]?.map { Case(it.substringAfterLast('.'), it) }

    override fun isObject(type: String): Boolean = type in objects

    override fun isNullable(type: String): Boolean = type.endsWith("?")

    override fun nonNull(type: String): String = type.removeSuffix("?")

    override fun valueClass(type: String): Param<String>? = valueClasses[type]

    override fun container(type: String): Container<String>? = containers[type]

    /** A case is written `Parent.Case`; anything else is not one. */
    override fun caseName(type: String): String? = type.takeIf { '.' in it }?.substringAfterLast('.')
}

fun param(name: String, type: String, hasDefault: Boolean = false): Param<String> = Param(name, type, hasDefault)

fun primary(vararg params: Param<String>): Construction<String> = Construction.Primary(params.toList())
