package example.cookbook.layers

import io.github.matthewjones372.kimney.into
import io.github.matthewjones372.kimney.transformInto

// The domain: identities and quantities are types, not Longs and Ints.
@JvmInline value class OrderId(val value: Long)

@JvmInline value class CustomerId(val value: Long)

@JvmInline value class Sku(val code: String)

@JvmInline value class Quantity(val units: Int)

enum class OrderStatus { PLACED, SHIPPED }

data class OrderLine(val sku: Sku, val quantity: Quantity)

data class Order(val id: OrderId, val customerId: CustomerId, val lines: List<OrderLine>, val status: OrderStatus)

// The way in: what the API accepts, and the command it becomes.
data class LineRequest(val sku: String, val quantity: Int)

data class PlaceOrderRequest(val customerId: Long, val lines: List<LineRequest>)

data class PlaceOrder(val customerId: CustomerId, val lines: List<OrderLine>)

// The way out: what happened, what is stored, and what the API answers.
data class OrderPlaced(val orderId: OrderId, val customerId: CustomerId, val lines: List<OrderLine>)

enum class StatusColumn { PLACED, SHIPPED }

data class OrderRow(val id: Long, val customerId: Long, val status: StatusColumn)

enum class StatusView { PLACED, SHIPPED, UNKNOWN }

data class LineView(val sku: String, val quantity: Int)

data class OrderView(val id: Long, val lines: List<LineView>, val status: StatusView)

// Five crossings between six shapes, one line each.
fun PlaceOrderRequest.toCommand(): PlaceOrder = transformInto()

fun PlaceOrder.toOrder(id: OrderId): Order = into<_, Order>()
    .withFieldConst(Order::id, id)
    .withFieldConst(Order::status, OrderStatus.PLACED)
    .transform()

fun Order.placed(): OrderPlaced = into<_, OrderPlaced>().withFieldRenamed(Order::id, OrderPlaced::orderId).transform()

fun Order.toRow(): OrderRow = transformInto()

fun Order.toView(): OrderView = transformInto()
