// MODULE: lib
// FILE: lib.kt
package lib

sealed interface Result {
    data class Ok(val value: Int) : Result
    data class Err(val message: String) : Result
}

sealed interface ResultDto {
    data class Ok(val value: Int) : ResultDto
    data class Err(val message: String, val code: Int = 500) : ResultDto
}

// MODULE: main(lib)
// FILE: main.kt
import io.github.matthewjones372.kimney.transformInto
import lib.Result
import lib.ResultDto

fun box(): String {
    val mapped = listOf(Result.Ok(1), Result.Err("no")).map { it.transformInto<ResultDto>() }
    return if (mapped == listOf(ResultDto.Ok(1), ResultDto.Err("no", 500))) "OK" else "Fail: $mapped"
}
