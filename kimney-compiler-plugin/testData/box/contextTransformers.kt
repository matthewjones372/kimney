import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into
import io.github.matthewjones372.kimney.transformInto

data class Money(val pence: Long)
data class MoneyDto(val pounds: String)
data class Invoice(val total: Money, val lines: List<Money>)
data class InvoiceDto(val total: MoneyDto, val lines: List<MoneyDto>, val note: String)
data class Summary(val total: Money)
data class SummaryDto(val total: MoneyDto)

val moneyToDto = Transformer<Money, MoneyDto> { MoneyDto("GBP" + it.pence / 100) }

context(money: Transformer<Money, MoneyDto>)
fun summary(s: Summary): SummaryDto = s.transformInto<SummaryDto>()

context(money: Transformer<Money, MoneyDto>)
fun nested(s: Summary): SummaryDto {
    fun inner(): SummaryDto = s.transformInto<SummaryDto>()
    return inner()
}

fun box(): String {
    val s = Summary(Money(500))
    val byFunction = context(moneyToDto) { summary(s) }
    val byLambda = context(moneyToDto) { s.transformInto<SummaryDto>() }
    val byNested = context(moneyToDto) { nested(s) }
    val chain = context(moneyToDto) {
        Invoice(Money(1200), listOf(Money(300))).into<_, InvoiceDto>().withFieldConst(InvoiceDto::note, "x").transform()
    }
    val expected = SummaryDto(MoneyDto("GBP5"))
    return when {
        byFunction != expected -> "Fail function: $byFunction"
        byLambda != expected -> "Fail lambda: $byLambda"
        byNested != expected -> "Fail nested: $byNested"
        chain != InvoiceDto(MoneyDto("GBP12"), listOf(MoneyDto("GBP3")), "x") -> "Fail chain: $chain"
        else -> "OK"
    }
}
