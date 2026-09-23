import io.github.matthewjones372.kimney.transformInto

@JvmInline value class UserId(val raw: Long)
@JvmInline value class OrderId(val value: Long)

data class User(val id: UserId, val manager: UserId?)
data class UserDto(val id: Long, val manager: Long?)
data class Order(val id: Long)
data class OrderDto(val id: OrderId)

fun box(): String {
    val dto = User(UserId(7), null).transformInto<UserDto>()
    val order = Order(9).transformInto<OrderDto>()
    val rewrapped = UserId(3).transformInto<OrderId>()
    return when {
        dto != UserDto(7, null) -> "Fail: $dto"
        order != OrderDto(OrderId(9)) -> "Fail: $order"
        rewrapped != OrderId(3) -> "Fail: $rewrapped"
        else -> "OK"
    }
}
