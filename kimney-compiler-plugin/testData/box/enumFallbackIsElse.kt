// A fallback is also the `else`: an entry compiled in after this call lands there rather than throwing.
// CHECK_BYTECODE_TEXT
// 0 NEW kotlin/NoWhenBranchMatchedException
import io.github.matthewjones372.kimney.into

enum class Wire { ACTIVE, LEGACY }
enum class StatusDto { ACTIVE, UNKNOWN }

fun Wire.toDto(): StatusDto = into<_, StatusDto>().withEnumFallback(StatusDto.UNKNOWN).transform()

fun box(): String {
    val mapped = Wire.entries.map { it.toDto() }
    return if (mapped == listOf(StatusDto.ACTIVE, StatusDto.UNKNOWN)) "OK" else "Fail: $mapped"
}
