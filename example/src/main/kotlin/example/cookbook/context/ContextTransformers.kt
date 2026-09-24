package example.cookbook.context

import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.transformInto

data class Money(val pence: Long, val currency: String)

data class MoneyDto(val display: String)

data class Invoice(val total: Money, val tax: Money)

data class InvoiceDto(val total: MoneyDto, val tax: MoneyDto)

/** How money is shown, decided once for the whole API layer. */
val showMoney = Transformer<Money, MoneyDto> {
    MoneyDto("${it.currency} ${it.pence / 100}.${(it.pence % 100).toString().padStart(2, '0')}")
}

// `this.` is required: a context parameter is also an `Any?`, so a bare transformInto() could mean either.
context(money: Transformer<Money, MoneyDto>)
fun Invoice.toDto(): InvoiceDto = this.transformInto()

fun present(invoice: Invoice): InvoiceDto = context(showMoney) { invoice.toDto() }
