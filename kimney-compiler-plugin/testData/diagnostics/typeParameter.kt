// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

data class User(val name: String)

fun <X> anything(user: User): X = <!KIMNEY_CANNOT_TRANSFORM!>user.transformInto<X>()<!>
