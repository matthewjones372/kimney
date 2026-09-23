import io.github.matthewjones372.kimney.into

data class Address(val street: String)
data class User(val name: String, val address: Address)
data class AddressDto(val street: String, val country: String = "GB")
data class UserDto(val name: String, val address: AddressDto, val source: String)

fun box(): String {
    val dto = User("Ada", Address("1 Loop Rd")).into<_, UserDto>()
        .withFieldConst(UserDto::source, "import")
        .transform()
    return if (dto == UserDto("Ada", AddressDto("1 Loop Rd", "GB"), "import")) "OK" else "Fail: $dto"
}
