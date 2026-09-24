package example.cookbook.partial

import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.transformIntoPartial

@JvmInline
value class Email(val address: String) {
    init {
        require("@" in address) { "is not an email address" }
    }
}

// What arrives over the wire: anything may be missing.
data class SignupForm(val email: String?, val name: String?, val referrals: List<String?>)

// What the domain accepts: nothing is.
data class Signup(val email: Email, val name: String, val referrals: List<Email>)

fun SignupForm.validate(): Partial<Signup> = transformIntoPartial()
