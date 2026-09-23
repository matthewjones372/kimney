import io.github.matthewjones372.kimney.transformInto

data class Address(val street: String)
data class AddressDto(val street: String)

var reads = 0

class User {
    val address: Address?
        get() {
            reads++
            return Address("1 Loop Rd")
        }
}

data class UserDto(val address: AddressDto?)

fun box(): String {
    val dto = User().transformInto<UserDto>()
    return if (reads == 1 && dto == UserDto(AddressDto("1 Loop Rd"))) "OK" else "Fail: reads=$reads, $dto"
}
