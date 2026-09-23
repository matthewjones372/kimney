import io.github.matthewjones372.kimney.transformInto

sealed class Event {
    sealed class Payment : Event() {
        data class Captured(val amount: Long) : Payment()
        data object Refused : Payment()
    }
    data class Login(val user: String) : Event()
}

sealed class EventDto {
    sealed class Payment : EventDto() {
        data class Captured(val amount: Long) : Payment()
        data object Refused : Payment()
    }
    data class Login(val user: String) : EventDto()
}

fun box(): String {
    val events = listOf(Event.Payment.Captured(5), Event.Payment.Refused, Event.Login("ada"))
    val mapped = events.map { it.transformInto<EventDto>() }
    val expected = listOf(EventDto.Payment.Captured(5), EventDto.Payment.Refused, EventDto.Login("ada"))
    return if (mapped == expected) "OK" else "Fail: $mapped"
}
