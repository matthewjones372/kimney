import io.github.matthewjones372.kimney.transformInto

enum class Status { ACTIVE, SUSPENDED }
enum class StatusDto { UNKNOWN, SUSPENDED, ACTIVE }

data class Account(val id: Long, val status: Status)
data class AccountDto(val id: Long, val status: StatusDto)

fun box(): String {
    val direct = Status.values().map { it.transformInto<StatusDto>() }
    val nested = Account(7, Status.SUSPENDED).transformInto<AccountDto>()
    return when {
        direct != listOf(StatusDto.ACTIVE, StatusDto.SUSPENDED) -> "Fail: $direct"
        nested != AccountDto(7, StatusDto.SUSPENDED) -> "Fail: $nested"
        else -> "OK"
    }
}
