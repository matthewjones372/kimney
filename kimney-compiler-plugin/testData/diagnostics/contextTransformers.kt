// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into
import io.github.matthewjones372.kimney.transformInto

data class Money(val pence: Long)
data class MoneyDto(val pounds: String)
data class Invoice(val total: Money)
data class InvoiceDto(val total: MoneyDto)
data class Note(val text: String)
data class NoteDto(val text: String)

val moneyToDto = Transformer<Money, MoneyDto> { MoneyDto((it.pence / 100).toString()) }

context(money: Transformer<Money, MoneyDto>)
fun byFunction(invoice: Invoice): InvoiceDto = invoice.transformInto<InvoiceDto>()

fun byLambda(invoice: Invoice): InvoiceDto = context(moneyToDto) { invoice.transformInto<InvoiceDto>() }

context(money: Transformer<Money, MoneyDto>)
fun unusedHereIsFine(note: Note): NoteDto = note.transformInto<NoteDto>()

context(money: Transformer<Money, MoneyDto>)
fun ambiguous(invoice: Invoice): InvoiceDto =
    <!KIMNEY_CANNOT_TRANSFORM!>invoice.into<_, InvoiceDto>().withTransformer(moneyToDto).transform()<!>

fun withoutOne(invoice: Invoice): InvoiceDto = <!KIMNEY_CANNOT_TRANSFORM!>invoice.transformInto<InvoiceDto>()<!>
