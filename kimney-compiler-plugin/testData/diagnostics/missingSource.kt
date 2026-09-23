// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

data class Address(val street: String)
data class User(val name: String, val address: Address)

data class AddressDto(val street: String, val zip: String)
data class UserDto(val name: String, val email: String, val address: AddressDto)

fun convert(user: User): UserDto = <!KIMNEY_CANNOT_TRANSFORM!>user.transformInto<UserDto>()<!>
