package example.cookbook.first

import dev.surefold.kimney.transformInto

data class User(val name: String, val email: String, val admin: Boolean)

data class UserDto(val name: String, val email: String)

fun User.toDto(): UserDto = transformInto()
