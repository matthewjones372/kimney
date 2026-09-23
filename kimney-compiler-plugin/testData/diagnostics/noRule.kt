// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

data class Order(val count: Int, val tags: List<Int>)
data class OrderDto(val count: Long, val tags: List<String>)

fun order(order: Order): OrderDto = <!KIMNEY_CANNOT_TRANSFORM!>order.transformInto<OrderDto>()<!>

fun root(n: Int): String = <!KIMNEY_CANNOT_TRANSFORM!>n.transformInto<String>()<!>

data class Box<A>(val value: A)

fun substituted(box: Box<Int>): Box<Long> = <!KIMNEY_CANNOT_TRANSFORM!>box.transformInto<Box<Long>>()<!>

data class Maybe(val name: String?)
data class Surely(val name: String)

fun nullable(maybe: Maybe): Surely = <!KIMNEY_CANNOT_TRANSFORM!>maybe.transformInto<Surely>()<!>
