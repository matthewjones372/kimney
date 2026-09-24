package example.cookbook

import example.cookbook.layers.CustomerId
import example.cookbook.layers.LineRequest
import example.cookbook.layers.LineView
import example.cookbook.layers.OrderId
import example.cookbook.layers.OrderLine
import example.cookbook.layers.OrderPlaced
import example.cookbook.layers.OrderRow
import example.cookbook.layers.OrderView
import example.cookbook.layers.PlaceOrderRequest
import example.cookbook.layers.Quantity
import example.cookbook.layers.Sku
import example.cookbook.layers.StatusColumn
import example.cookbook.layers.StatusView
import example.cookbook.layers.placed
import example.cookbook.layers.toCommand
import example.cookbook.layers.toOrder
import example.cookbook.layers.toRow
import example.cookbook.layers.toView
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** One order through every layer of docs/why.md's example, each crossing checked. */
class LayersTest {

    private val request = PlaceOrderRequest(customerId = 7, lines = listOf(LineRequest("A-1", quantity = 2)))
    private val line = OrderLine(Sku("A-1"), Quantity(2))
    private val order = request.toCommand().toOrder(OrderId(100))

    @Test
    fun `the request becomes a command in domain types`() {
        request.toCommand().lines shouldBe listOf(line)
        request.toCommand().customerId shouldBe CustomerId(7)
    }

    @Test
    fun `the command becomes an order, the event, the row and the view`() {
        order.placed() shouldBe OrderPlaced(OrderId(100), CustomerId(7), listOf(line))
        order.toRow() shouldBe OrderRow(100, 7, StatusColumn.PLACED)
        order.toView() shouldBe OrderView(100, listOf(LineView("A-1", 2)), StatusView.PLACED)
    }
}
