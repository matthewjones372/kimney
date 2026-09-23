package io.github.matthewjones372.kimney.derive

/** Where a failure is, from the root target: `UserDto.address.zip`. */
data class Path(val root: String, val fields: List<String> = emptyList()) {
    operator fun div(field: String): Path = copy(fields = fields + field)

    override fun toString(): String = (listOf(root) + fields).joinToString(".")
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

    data class Recursive(override val path: Path, override val type: String, val source: String) : Failure {
        override val reason get() = "$source → $type contains itself, and recursive types are not supported yet."
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
                "with property references like $target::name."
    }
}
