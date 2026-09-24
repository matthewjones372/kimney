// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

@JvmInline value class UserId(val raw: Long)
enum class Tag { NEW }
enum class TagDto { NEW }

data class Line(val sku: String, val qty: Int?)
data class LineDto(val sku: String, val qty: Int?)
data class LooseLine(val code: String)

data class Order(
    val lines: List<Line>,
    val tags: Set<Tag>,
    val owners: Map<UserId, Line>,
    val history: Array<Line>,
    val notes: List<String?>,
)
data class OrderDto(
    val lines: Iterable<LineDto>,
    val tags: Set<TagDto>,
    val owners: Map<Long, LineDto>,
    val history: Array<LineDto>,
    val notes: List<String?>,
)

data class LooseOrder(val lines: List<LooseLine>, val tags: Set<Tag>, val keyed: Map<Line, Tag>)
data class StrictOrder(val lines: List<LineDto>, val tags: List<TagDto>, val keyed: Map<LineDto, TagDto>)

fun valid(order: Order): OrderDto = order.transformInto<OrderDto>()

fun strict(order: LooseOrder): StrictOrder = <!KIMNEY_CANNOT_TRANSFORM!>order.transformInto<StrictOrder>()<!>

fun nullElements(notes: List<String?>): List<String> = <!KIMNEY_CANNOT_TRANSFORM!>notes.transformInto<List<String>>()<!>

fun mutable(lines: List<Line>): MutableList<LineDto> = lines.transformInto<MutableList<LineDto>>()

fun mutableCrossing(tags: MutableSet<Tag>): MutableList<TagDto> = <!KIMNEY_CANNOT_TRANSFORM!>tags.transformInto<MutableList<TagDto>>()<!>
