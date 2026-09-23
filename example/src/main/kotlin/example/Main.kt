package example

import io.github.matthewjones372.kimney.into
import io.github.matthewjones372.kimney.transformInto

const val THIS_YEAR = 2026

data class Address(val street: String, val zip: String)

data class User(val fullName: String, val born: Int, val address: Address, val admin: Boolean)

data class AddressDto(val street: String, val zip: String, val country: String = "GB")

data class UserDto(val name: String, val address: AddressDto)

data class AuditedUser(val name: String, val age: Int, val source: String, val address: AddressDto)

fun main() {
    val user = User("Ada Lovelace", 1815, Address("1 Loop Rd", "N1 9GU"), admin = true)

    println(user.into<_, UserDto>().withFieldRenamed(User::fullName, UserDto::name).transform())

    println(
        user.into<_, AuditedUser>()
            .withFieldRenamed(User::fullName, AuditedUser::name)
            .withFieldComputed(AuditedUser::age) { THIS_YEAR - it.born }
            .withFieldConst(AuditedUser::source, "import")
            .transform(),
    )

    println(Address("2 Loop Rd", "N1 9GV").transformInto<AddressDto>())
}
