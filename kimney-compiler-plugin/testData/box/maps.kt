// CHECK_BYTECODE_TEXT
// 0 LambdaMetafactory
import io.github.matthewjones372.kimney.transformInto

@JvmInline value class UserId(val raw: Long)
enum class Tier { GOLD, SILVER }
enum class TierDto { SILVER, GOLD }

data class Money(val pence: Long)
data class MoneyDto(val pence: Long, val currency: String = "GBP")

data class Ledger(val balances: Map<UserId, Money>, val tiers: Map<String, Tier?>)
data class LedgerDto(val balances: Map<Long, MoneyDto>, val tiers: Map<String, TierDto?>)

fun box(): String {
    val balances = linkedMapOf(UserId(3) to Money(30), UserId(1) to Money(10))
    val dto = Ledger(balances, mapOf("a" to Tier.GOLD, "b" to null)).transformInto<LedgerDto>()
    return when {
        dto.balances.toList() != listOf(3L to MoneyDto(30), 1L to MoneyDto(10)) -> "Fail balances: ${dto.balances}"
        dto.tiers != mapOf("a" to TierDto.GOLD, "b" to null) -> "Fail tiers: ${dto.tiers}"
        else -> "OK"
    }
}
