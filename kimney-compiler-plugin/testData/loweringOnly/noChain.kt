import io.github.matthewjones372.kimney.Into

data class User(val name: String)
data class UserDto(val name: String)

fun finish(chain: Into<User, UserDto>): UserDto = chain.<!KIMNEY_INTERNAL_ERROR!>transform()<!>
