package example

import io.github.matthewjones372.kimney.KimneyNotApplied
import io.github.matthewjones372.kimney.transformInto

data class User(val name: String)

data class UserDto(val name: String)

fun main() {
    // Nothing lowers until spec 0002, so this reaches the stub even with the plugin applied.
    try {
        println(User("Ada").transformInto<UserDto>())
    } catch (e: KimneyNotApplied) {
        println(e.message)
    }
}
