package io.github.matthewjones372.kimney.derive

/** Where a failure is, from the root target: `UserDto.address.zip`. */
data class Path(val root: String, val fields: List<String> = emptyList()) {
    operator fun div(field: String): Path = copy(fields = fields + field)

    // `[]` and `[key]` attach to the field before them: `OrderDto.lines[].sku`.
    override fun toString(): String =
        fields.fold(root) { path, field -> if (field.startsWith("[")) "$path$field" else "$path.$field" }
}

/** Why a target could not be derived. Each variant owns its message; types arrive already rendered. */
sealed interface Failure {
    val path: Path

    /** The target type at [path], where there is one. */
    val type: String?

    val reason: String

    val line: String
        get() = when {
            path.fields.isEmpty() -> "${type ?: path} — $reason"
            type == null -> "$path — $reason"
            else -> "$path: $type — $reason"
        }

    data class MissingSource(
        override val path: Path,
        override val type: String,
        val source: String,
        val owner: String,
    ) : Failure {
        private val field get() = path.fields.last()

        // Overrides name top-level fields only, so a nested field is not offered one.
        override val reason
            get() = if (path.fields.size == 1) {
                "$source has no property '$field'. Add it to $source, give $owner.$field a default value, " +
                    "or add .withFieldConst($owner::$field, …)."
            } else {
                "$source has no property '$field'. Add it to $source, or give $owner.$field a default value."
            }
    }

    data class NoPrimaryConstructor(
        override val path: Path,
        override val type: String,
        val why: String,
    ) : Failure {
        override val reason get() = "$type has no public primary constructor: $why."
    }

    data class NoRuleFor(override val path: Path, override val type: String, val source: String) : Failure {
        override val reason get() = "no rule transforms $source into $type."
    }

    data class NullableToNonNull(
        override val path: Path,
        override val type: String,
        val source: String,
        val owner: String?,
        val origin: String?,
    ) : Failure {
        override val reason get() = "${origin?.let { "$it is $source" } ?: "the source is $source"}, " +
            "and a null has nowhere to go. ${fix()}"

        // Overrides name top-level fields only, so a nested field is not offered one.
        private fun fix(): String {
            val field = path.fields.lastOrNull()
            return when {
                owner == null -> "Transform into $type? instead."

                path.fields.size == 1 ->
                    "Make $owner.$field nullable, or fill it with .withFieldComputed($owner::$field) { … }."

                else -> "Make $owner.$field nullable."
            }
        }
    }

    data class ContainerMismatch(
        override val path: Path,
        override val type: String,
        val from: Container.Kind,
        val to: Container.Kind,
        val owner: String?,
    ) : Failure {
        override val reason: String
            get() {
                val field = path.fields.lastOrNull()
                val fix = if (owner != null && path.fields.size == 1) {
                    " Fill it with .withFieldComputed($owner::$field) { … }."
                } else {
                    ""
                }
                return "${from.display} is not turned into ${to.display}.$fix"
            }
    }

    data class KeyMayCollide(override val path: Path, override val type: String, val source: String) : Failure {
        override val reason
            get() = "keys are transformed only as themselves or through a value class, since $source into $type " +
                "could turn two keys into one."
    }

    data class AmbiguousTransformer(
        override val path: Path,
        override val type: String,
        val source: String,
        val chain: List<Int>,
        val context: List<String> = emptyList(),
    ) : Failure {
        override val reason: String
            get() {
                val count = chain.size + context.size
                val sources = listOfNotNull(
                    chain.takeIf { it.isNotEmpty() }?.let { "withTransformer " + and(it.map { i -> "#${i + 1}" }) },
                ) + context.map { "context parameter '$it'" }
                return "${if (count == 2) "two" else "$count"} transformers fit $source → $type: ${and(sources)}. " +
                    "Pass one."
            }

        private fun and(items: List<String>): String =
            if (items.size == 1) items.single() else items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    /** [inner], with a transformer offered for the nested pair it sits in. */
    data class WithTransformerHint(val inner: Failure, val source: String, val target: String) : Failure {
        override val path get() = inner.path
        override val type get() = inner.type
        override val reason
            get() = "${inner.reason} Or map $source → $target with " +
                ".withTransformer(Transformer<$source, $target> { … })."
    }

    data class MissingCase(
        override val path: Path,
        override val type: String,
        val case: String,
        val kind: String,
    ) : Failure {
        override val reason get() = "$case has no $kind of the same name in $type."
    }

    data class NotAParameter(override val path: Path, val method: String, val owner: String) : Failure {
        override val type: String? get() = null
        override val reason
            get() = "$method names '${path.fields.last()}', which is not a constructor parameter of $owner."
    }

    data class DuplicateOverride(override val path: Path, val methods: List<String>) : Failure {
        override val type: String? get() = null
        override val reason: String
            get() {
                val times = if (methods.size == 2) "twice" else "${methods.size} times"
                val by = methods.dropLast(1).joinToString(", ") + " and " + methods.last()
                return "overridden $times, by $by. Keep one."
            }
    }

    data class OverrideTypeMismatch(
        override val path: Path,
        override val type: String,
        val method: String,
        val given: String,
    ) : Failure {
        override val reason get() = "$method gives $given, which is not a $type."
    }

    data class UnreadableSource(
        override val path: Path,
        override val type: String,
        val source: String,
        val property: String,
    ) : Failure {
        override val reason
            get() = "withFieldRenamed reads $source.$property, which kimney cannot read: " +
                "it is inherited, an extension or not public."
    }

    /** Found by an adapter reading the call, not by the engine: the chain is syntax. */
    data class OverrideNotStatic(override val path: Path, val target: String) : Failure {
        override val type: String? get() = null
        override val reason
            get() = "the overrides must be one chain from into() to .transform(), written as a single expression " +
                "with property references like $target::name and a lambda for withFieldComputed."
    }
}

/** A warning rather than a [Failure]: the transformation still derives, just not the way its author wrote it. */
fun unusedTransformer(source: String, target: String): String =
    "withTransformer($source → $target) is not used: no pair below the root fits it. " +
        "A type it names may have changed."
