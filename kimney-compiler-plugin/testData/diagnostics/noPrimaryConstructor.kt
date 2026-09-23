// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

data class Source(val id: Long)

class Hidden private constructor(val id: Long)

class SecondaryOnly {
    val id: Long

    constructor(id: Long) {
        this.id = id
    }
}

fun hidden(source: Source): Hidden = <!KIMNEY_CANNOT_TRANSFORM!>source.transformInto<Hidden>()<!>

fun secondary(source: Source): SecondaryOnly = <!KIMNEY_CANNOT_TRANSFORM!>source.transformInto<SecondaryOnly>()<!>
