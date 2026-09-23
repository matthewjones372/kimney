// CHECK_BYTECODE_TEXT
// 0 INVOKEVIRTUAL .*\.ordinal \(\)I
// MODULE: lib
// FILE: lib.kt
package lib

enum class Status { ACTIVE, SUSPENDED }
enum class StatusDto { SUSPENDED, ACTIVE }

// MODULE: main(lib)
// FILE: main.kt
import io.github.matthewjones372.kimney.transformInto
import lib.Status
import lib.StatusDto

fun box(): String {
    val mapped = Status.SUSPENDED.transformInto<StatusDto>() to Status.ACTIVE.transformInto<StatusDto>()
    return if (mapped == (StatusDto.SUSPENDED to StatusDto.ACTIVE)) "OK" else "Fail: $mapped"
}
