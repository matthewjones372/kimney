import io.github.matthewjones372.kimney.KimneyNotApplied
import io.github.matthewjones372.kimney.transformInto

data class User(val name: String)
data class UserDto(val name: String)

// The checker accepts this pair, and nothing lowers it yet, so the stub is reached.
fun box(): String =
    try {
        User("Ada").transformInto<UserDto>()
        "Fail: the stub returned"
    } catch (e: KimneyNotApplied) {
        "OK"
    }
