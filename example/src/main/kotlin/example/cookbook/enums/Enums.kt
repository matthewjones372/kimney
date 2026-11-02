package example.cookbook.enums

import dev.surefold.kimney.transformInto

enum class Status { ACTIVE, SUSPENDED }

enum class StatusDto { ACTIVE, SUSPENDED, UNKNOWN }

fun Status.toDto(): StatusDto = transformInto()
