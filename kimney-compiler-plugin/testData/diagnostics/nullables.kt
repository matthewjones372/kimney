// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

@JvmInline value class UserId(val raw: Long)
@JvmInline value class OrderId(val value: Long)
@JvmInline value class Label(val text: String)

enum class Status { ACTIVE }
enum class StatusDto { ACTIVE }

data class Address(val street: String, val zip: String?)
data class AddressDto(val street: String, val zip: String?)
data class User(val id: UserId, val name: String?, val address: Address?, val status: Status?)
data class UserDto(val id: Long, val name: String?, val address: AddressDto?, val status: StatusDto?)

data class StrictAddressDto(val street: String, val zip: String)
data class StrictDto(val id: Long, val name: String, val address: StrictAddressDto?, val status: StatusDto?)

data class UserIdDto(val raw: Long)

fun valid(user: User): UserDto = user.transformInto<UserDto>()

fun wrapped(id: Long): UserId = id.transformInto<UserId>()

fun rewrapped(id: UserId): OrderId = id.transformInto<OrderId>()

fun strict(user: User): StrictDto = <!KIMNEY_CANNOT_TRANSFORM!>user.transformInto<StrictDto>()<!>

fun root(name: String?): String = <!KIMNEY_CANNOT_TRANSFORM!>name.transformInto<String>()<!>

fun inner(id: UserId): Label = <!KIMNEY_CANNOT_TRANSFORM!>id.transformInto<Label>()<!>

fun unwrapsFirst(id: UserId): UserIdDto = <!KIMNEY_CANNOT_TRANSFORM!>id.transformInto<UserIdDto>()<!>
