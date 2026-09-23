// MODULE: lib
// FILE: lib.kt
package lib

data class Address(val street: String)
data class User(val name: String, val address: Address)

data class AddressDto(val street: String, val country: String = "GB")
data class UserDto(val name: String, val address: AddressDto)

// MODULE: main(lib)
// FILE: main.kt
import io.github.matthewjones372.kimney.transformInto
import lib.Address
import lib.AddressDto
import lib.User
import lib.UserDto

fun box(): String {
    val dto = User("Ada", Address("1 Loop Rd")).transformInto<UserDto>()
    return if (dto == UserDto("Ada", AddressDto("1 Loop Rd", "GB"))) "OK" else "Fail: $dto"
}
