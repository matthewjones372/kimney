package example

import io.github.matthewjones372.kimney.transformInto

data class Address(val street: String, val zip: String)

data class User(val name: String, val address: Address, val admin: Boolean)

data class AddressDto(val street: String, val zip: String, val country: String = "GB")

data class UserDto(val name: String, val address: AddressDto)

fun main() {
    val user = User("Ada", Address("1 Loop Rd", "N1 9GU"), admin = true)
    println(user.transformInto<UserDto>())
}
