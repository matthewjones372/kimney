// A fallback is also the `else`: a case compiled in after this call lands there rather than throwing.
// CHECK_BYTECODE_TEXT
// 0 NEW kotlin/NoWhenBranchMatchedException
import io.github.matthewjones372.kimney.into

sealed interface Event {
    data class Opened(val id: Long) : Event
    data class Archived(val id: Long) : Event
}

sealed interface Message {
    data class Opened(val id: Long) : Message
    data object Unsupported : Message
}

fun Event.toMessage(): Message = into<_, Message>().withSealedFallback(Message.Unsupported).transform()

fun box(): String {
    val mapped = listOf(Event.Opened(1), Event.Archived(2)).map { it.toMessage() }
    return if (mapped == listOf(Message.Opened(1), Message.Unsupported)) "OK" else "Fail: $mapped"
}
