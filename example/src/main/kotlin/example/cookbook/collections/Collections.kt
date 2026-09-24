package example.cookbook.collections

import io.github.matthewjones372.kimney.transformInto

@JvmInline
value class Sku(val code: String)

enum class Tag { GIFT, FRAGILE }

enum class TagDto { GIFT, FRAGILE }

data class Line(val sku: Sku, val quantity: Int)

data class LineDto(val sku: String, val quantity: Int)

data class Order(val lines: List<Line>, val tags: Set<Tag>, val stock: Map<Sku, Int>)

data class OrderDto(val lines: List<LineDto>, val tags: Set<TagDto>, val stock: Map<String, Int>)

fun toDto(order: Order): OrderDto = order.transformInto<OrderDto>()
