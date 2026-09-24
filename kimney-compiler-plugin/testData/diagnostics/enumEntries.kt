// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.into

enum class Status { ACTIVE, ARCHIVED }
enum class StatusDto { ACTIVE, INACTIVE, UNKNOWN }
enum class Other { A }
enum class OtherDto { A, B }

val archived = Status.ARCHIVED

fun notAnEntry(status: Status): StatusDto = <!KIMNEY_CANNOT_TRANSFORM!>status.into<_, StatusDto>()
    .withEnumEntryRenamed(archived, StatusDto.INACTIVE)
    .transform()<!>

fun twice(status: Status): StatusDto = <!KIMNEY_CANNOT_TRANSFORM!>status.into<_, StatusDto>()
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .withEnumFallback(StatusDto.UNKNOWN)
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.UNKNOWN)
    .withEnumFallback(StatusDto.INACTIVE)
    .transform()<!>

fun unusedRename(status: Status): StatusDto = <!KIMNEY_UNUSED_ENUM_MAPPING!>status.into<_, StatusDto>()
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .withEnumEntryRenamed(Other.A, OtherDto.B)<!>
    .transform()

fun unusedFallback(status: Status): StatusDto = <!KIMNEY_UNUSED_ENUM_MAPPING!>status.into<_, StatusDto>()
    .withEnumFallback(OtherDto.B)<!>
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .transform()
