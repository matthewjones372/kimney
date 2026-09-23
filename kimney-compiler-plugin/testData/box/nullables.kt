import io.github.matthewjones372.kimney.transformInto

enum class Status { ACTIVE }
enum class StatusDto { ACTIVE }

data class Address(val street: String)
data class AddressDto(val street: String, val country: String = "GB")
data class User(val name: String, val address: Address?, val status: Status?)
data class UserDto(val name: String?, val address: AddressDto?, val status: StatusDto?)

fun box(): String {
    val present = User("Ada", Address("1 Loop Rd"), Status.ACTIVE).transformInto<UserDto>()
    val absent = User("Bob", null, null).transformInto<UserDto>()
    return when {
        present != UserDto("Ada", AddressDto("1 Loop Rd"), StatusDto.ACTIVE) -> "Fail: $present"
        absent != UserDto("Bob", null, null) -> "Fail: $absent"
        else -> "OK"
    }
}
