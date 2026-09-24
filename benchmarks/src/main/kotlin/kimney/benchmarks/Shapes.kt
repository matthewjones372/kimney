package kimney.benchmarks

// The types each pair of benchmarks maps between: the shapes a layered service
// moves an order through, and a form validated at its edge.

@JvmInline value class OrderId(val value: Long)

@JvmInline value class CustomerId(val value: Long)

@JvmInline value class Sku(val code: String)

@JvmInline value class Quantity(val units: Int)

enum class OrderStatus { PLACED, SHIPPED }

data class OrderLine(val sku: Sku, val quantity: Quantity)

data class Order(val id: OrderId, val customerId: CustomerId, val lines: List<OrderLine>, val status: OrderStatus)

enum class StatusView { PLACED, SHIPPED, UNKNOWN }

data class LineView(val sku: String, val quantity: Int)

data class OrderView(val id: Long, val lines: List<LineView>, val status: StatusView)

data class LineRequest(val sku: String, val quantity: Int)

data class PlaceOrderRequest(val customerId: Long, val lines: List<LineRequest>)

data class PlaceOrder(val customerId: CustomerId, val lines: List<OrderLine>)

data class Address(val street: String, val city: String, val postcode: String)

data class Customer(val id: CustomerId, val name: String, val home: Address, val work: Address?)

data class AddressDto(val street: String, val city: String, val postcode: String, val country: String = "GB")

data class CustomerDto(val id: Long, val name: String, val home: AddressDto, val work: AddressDto?)

@JvmInline
value class Email(val address: String) {
    init {
        require("@" in address) { "is not an email address" }
    }
}

data class SignupForm(val email: String?, val name: String?, val referrals: List<String?>)

data class Signup(val email: Email, val name: String, val referrals: List<Email>)
