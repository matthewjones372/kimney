package kimney.benchmarks

import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.PartialError

// The same mappings as a careful person writes them: `map` for a list, a
// `when` for an enum, and a validator that collects every error with its path.

fun Order.toViewByHand(): OrderView = OrderView(
    id = id.value,
    lines = lines.map { LineView(it.sku.code, it.quantity.units) },
    status = when (status) {
        OrderStatus.PLACED -> StatusView.PLACED
        OrderStatus.SHIPPED -> StatusView.SHIPPED
    },
)

fun PlaceOrderRequest.toCommandByHand(): PlaceOrder = PlaceOrder(
    customerId = CustomerId(customerId),
    lines = lines.map { OrderLine(Sku(it.sku), Quantity(it.quantity)) },
)

fun Address.toDtoByHand(): AddressDto = AddressDto(street, city, postcode)

fun Customer.toDtoByHand(): CustomerDto = CustomerDto(id.value, name, home.toDtoByHand(), work?.toDtoByHand())

private fun email(text: String?, path: String, errors: MutableList<PartialError>): Email? = when {
    text == null -> null.also { errors += PartialError(path, "is null") }

    else -> try {
        Email(text)
    } catch (e: IllegalArgumentException) {
        null.also { errors += PartialError(path, e.message.orEmpty()) }
    }
}

fun SignupForm.validateByHand(): Partial<Signup> {
    val errors = mutableListOf<PartialError>()
    val email = email(email, "Signup.email", errors)
    if (name == null) errors += PartialError("Signup.name", "is null")
    val referrals = referrals.mapNotNull { email(it, "Signup.referrals[]", errors) }
    return if (email != null && name != null && errors.isEmpty()) {
        Partial.Ok(Signup(email, name, referrals))
    } else {
        Partial.Errors(errors)
    }
}
