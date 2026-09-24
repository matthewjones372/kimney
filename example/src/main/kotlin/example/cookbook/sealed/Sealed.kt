package example.cookbook.sealed

import io.github.matthewjones372.kimney.transformInto

sealed interface Payment {
    data class Card(val last4: String) : Payment

    data class Transfer(val iban: String) : Payment

    data object Cash : Payment
}

sealed interface PaymentDto {
    data class Card(val last4: String, val network: String = "unknown") : PaymentDto

    data class Transfer(val iban: String) : PaymentDto

    data object Cash : PaymentDto
}

fun toDto(payment: Payment): PaymentDto = payment.transformInto<PaymentDto>()
