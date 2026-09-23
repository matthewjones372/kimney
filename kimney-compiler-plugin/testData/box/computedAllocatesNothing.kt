// CHECK_BYTECODE_TEXT
// 0 INVOKEINTERFACE kotlin/jvm/functions/Function1.invoke
// 0 NEW .*Lambda
// 0 LambdaMetafactory
import io.github.matthewjones372.kimney.into

data class User(val born: Int)
data class UserDto(val age: Int)

fun box(): String {
    val dto = User(1815).into<_, UserDto>().withFieldComputed(UserDto::age) { 2026 - it.born }.transform()
    return if (dto.age == 211) "OK" else "Fail: $dto"
}
