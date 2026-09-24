// MODULE: lib
// FILE: lib.kt
package lib

import io.github.matthewjones372.kimney.Transformer

data class Money(val pence: Long)
data class MoneyDto(val pounds: String)

val moneyToDto = Transformer<Money, MoneyDto> { MoneyDto("%d.%02d".format(it.pence / 100, it.pence % 100)) }

// MODULE: main(lib)
// FILE: main.kt
import io.github.matthewjones372.kimney.into
import lib.Money
import lib.MoneyDto
import lib.moneyToDto

data class Invoice(val total: Money, val lines: List<Money>)
data class InvoiceDto(val total: MoneyDto, val lines: List<MoneyDto>)

fun box(): String {
    val dto = Invoice(Money(1250), listOf(Money(5))).into<_, InvoiceDto>().withTransformer(moneyToDto).transform()
    return if (dto == InvoiceDto(MoneyDto("12.50"), listOf(MoneyDto("0.05")))) "OK" else "Fail: $dto"
}
