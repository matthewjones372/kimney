import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.into

enum class Status { ACTIVE, PENDING, ARCHIVED }
enum class StatusDto { ACTIVE, PENDING, INACTIVE, UNKNOWN }
enum class Wire { ACTIVE, LEGACY, RETIRED }

data class Account(val id: Long, val status: Status, val history: List<Status>)
data class AccountDto(val id: Long, val status: StatusDto, val history: List<StatusDto>)
data class Form(val id: Long?, val status: Wire)
data class Imported(val id: Long, val status: StatusDto)

fun Status.toDto(): StatusDto = into<_, StatusDto>().withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE).transform()

fun Account.toDto(): AccountDto = into<_, AccountDto>()
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .transform()

fun Wire.toDto(): StatusDto = into<_, StatusDto>().withEnumFallback(StatusDto.UNKNOWN).transform()

fun Status.pendingIsActive(): StatusDto = into<_, StatusDto>()
    .withEnumEntryRenamed(Status.PENDING, StatusDto.ACTIVE)
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .transform()

fun Status.archivedIsPending(): Status = into<_, Status>().withEnumEntryRenamed(Status.ARCHIVED, Status.PENDING).transform()

fun Form.validate(): Partial<Imported> = into<_, Imported>().withEnumFallback(StatusDto.UNKNOWN).transformPartial()

fun box(): String {
    val direct = Status.entries.map { it.toDto() }
    val nested = Account(7, Status.ARCHIVED, listOf(Status.ACTIVE, Status.ARCHIVED)).toDto()
    val wire = Wire.entries.map { it.toDto() }
    val renamedOverName = Status.entries.map { it.pendingIsActive() }
    val self = Status.entries.map { it.archivedIsPending() }
    val partial = Form(1, Wire.RETIRED).validate()
    val failed = Form(null, Wire.ACTIVE).validate()
    return when {
        direct != listOf(StatusDto.ACTIVE, StatusDto.PENDING, StatusDto.INACTIVE) -> "Fail direct: $direct"
        nested != AccountDto(7, StatusDto.INACTIVE, listOf(StatusDto.ACTIVE, StatusDto.INACTIVE)) -> "Fail nested: $nested"
        wire != listOf(StatusDto.ACTIVE, StatusDto.UNKNOWN, StatusDto.UNKNOWN) -> "Fail wire: $wire"
        renamedOverName != listOf(StatusDto.ACTIVE, StatusDto.ACTIVE, StatusDto.INACTIVE) -> "Fail rename: $renamedOverName"
        self != listOf(Status.ACTIVE, Status.PENDING, Status.PENDING) -> "Fail self: $self"
        partial != Partial.Ok(Imported(1, StatusDto.UNKNOWN)) -> "Fail partial: $partial"
        failed !is Partial.Errors -> "Fail errors: $failed"
        else -> "OK"
    }
}
