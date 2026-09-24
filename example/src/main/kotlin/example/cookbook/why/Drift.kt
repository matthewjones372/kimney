package example.cookbook.why

import io.github.matthewjones372.kimney.transformInto

data class Signup(val email: String, val marketingConsent: Boolean)

// `marketingConsent` was added to both classes after the hand-written mapper below. Its default keeps old
// callers compiling, which is exactly why nothing tells the mapper it is now wrong.
data class SignupDto(val email: String, val marketingConsent: Boolean = false)

fun Signup.toDtoByHand(): SignupDto = SignupDto(email = email)

fun Signup.toDto(): SignupDto = transformInto()

enum class Plan { FREE, PRO, ENTERPRISE }

enum class PlanDto { FREE, PRO, UNKNOWN }

// Written when there were two plans. ENTERPRISE arrived later and fell into the `else` without a word.
fun Plan.toDtoByHand(): PlanDto = when (this) {
    Plan.FREE -> PlanDto.FREE
    Plan.PRO -> PlanDto.PRO
    else -> PlanDto.UNKNOWN
}
