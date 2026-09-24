package example.cookbook.entries

import io.github.matthewjones372.kimney.into

// The domain archives accounts; the API calls that inactive.
enum class Status { ACTIVE, SUSPENDED, ARCHIVED }

enum class StatusDto { ACTIVE, SUSPENDED, INACTIVE }

fun Status.toDto(): StatusDto = into<_, StatusDto>()
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .transform()

// A partner's enum grows without asking; everything we do not model is UNKNOWN.
enum class PartnerStatus { OPEN, CLOSED, ON_HOLD, ESCALATED }

enum class TicketStatus { OPEN, CLOSED, UNKNOWN }

fun PartnerStatus.toTicket(): TicketStatus = into<_, TicketStatus>()
    .withEnumFallback(TicketStatus.UNKNOWN)
    .transform()
