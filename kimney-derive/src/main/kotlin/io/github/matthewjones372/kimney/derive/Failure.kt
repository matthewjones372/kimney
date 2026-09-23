package io.github.matthewjones372.kimney.derive

/** Where a failure is, from the root target: `UserDto.address.zip`. */
data class Path(val root: String, val fields: List<String> = emptyList()) {
    operator fun div(field: String): Path = copy(fields = fields + field)

    override fun toString(): String = (listOf(root) + fields).joinToString(".")
}

/** Why a target could not be derived. Each variant owns its message; types arrive already rendered. */
sealed interface Failure {
    val path: Path

    /** The target type at [path]. */
    val type: String

    val reason: String

    val line: String
        get() = if (path.fields.isEmpty()) "$type — $reason" else "$path: $type — $reason"

    data class MissingSource(
        override val path: Path,
        override val type: String,
        val source: String,
        val owner: String,
    ) : Failure {
        private val field get() = path.fields.last()
        override val reason
            get() = "$source has no property '$field'. Add it to $source, or give $owner.$field a default value."
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
}
