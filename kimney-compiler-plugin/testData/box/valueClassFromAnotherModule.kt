// MODULE: lib
// FILE: lib.kt
package lib

@JvmInline value class UserId(val raw: Long)
data class User(val id: UserId)

// MODULE: main(lib)
// FILE: main.kt
import io.github.matthewjones372.kimney.transformInto
import lib.User
import lib.UserId

data class UserDto(val id: Long)

fun box(): String {
    val dto = User(UserId(42)).transformInto<UserDto>()
    val back = dto.transformInto<User>()
    return if (dto == UserDto(42) && back == User(UserId(42))) "OK" else "Fail: $dto, $back"
}
