// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.transformIntoPartial
import io.github.matthewjones372.kimney.transformInto

data class Form(val email: String?)
data class Signup(val email: String)
data class Strict(val email: String, val phone: String)

fun partialAllowsNull(form: Form): Partial<Signup> = form.transformIntoPartial<Signup>()

fun totalStillRefuses(form: Form): Signup = <!KIMNEY_CANNOT_TRANSFORM!>form.transformInto<Signup>()<!>

fun partialStillNeedsASource(form: Form): Partial<Strict> = <!KIMNEY_CANNOT_TRANSFORM!>form.transformIntoPartial<Strict>()<!>
