import dev.surefold.kimney.Partial
import dev.surefold.kimney.PartialError
import dev.surefold.kimney.into

data class Form(val email: String?, val name: String)
data class Signup(val email: String, val name: String, val source: String)

fun box(): String {
    val missing = Form(null, "Ada").into<_, Signup>().withFieldConst(Signup::source, "web").transformPartial()
    val present = Form("ada@example.com", "Ada").into<_, Signup>().withFieldConst(Signup::source, "web").transformPartial()
    return when {
        missing != Partial.Errors(listOf(PartialError("Signup.email", "is null"))) -> "Fail missing: $missing"
        present != Partial.Ok(Signup("ada@example.com", "Ada", "web")) -> "Fail present: $present"
        else -> "OK"
    }
}
