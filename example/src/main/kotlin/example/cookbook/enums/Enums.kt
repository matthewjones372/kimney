package example.cookbook.enums

import io.github.matthewjones372.kimney.transformInto

enum class Status { ACTIVE, SUSPENDED }

enum class StatusDto { ACTIVE, SUSPENDED, UNKNOWN }

fun toDto(status: Status): StatusDto = status.transformInto<StatusDto>()
