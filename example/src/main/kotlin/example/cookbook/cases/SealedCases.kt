package example.cookbook.cases

import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

sealed interface Event {
    data class Opened(val ticket: Long) : Event

    data class Escalated(val ticket: Long, val level: Int) : Event

    data class Merged(val ticket: Long, val into: Long) : Event

    data class Reacted(val ticket: Long, val emoji: String) : Event
}

sealed interface Message {
    data class Opened(val ticket: Long) : Message

    data class Raised(val ticket: Long, val level: Int) : Message

    data class Closed(val ticket: Long, val reason: String) : Message

    data object Ignored : Message
}

// Escalated is called Raised downstream; a merge closes the ticket; nothing else is sent on.
val merged = Transformer<Event.Merged, Message> { Message.Closed(it.ticket, "merged into ${it.into}") }

fun Event.toMessage(): Message = into<_, Message>()
    .withSealedCaseRenamed(Event.Escalated::class, Message.Raised::class)
    .withTransformer(merged)
    .withSealedFallback(Message.Ignored)
    .transform()
