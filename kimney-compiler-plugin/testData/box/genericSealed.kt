import io.github.matthewjones372.kimney.transformInto

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Err(val message: String) : Result<Nothing>
}
sealed interface ResultDto<out T> {
    data class Ok<T>(val value: T) : ResultDto<T>
    data class Err(val message: String, val code: Int = 400) : ResultDto<Nothing>
}

data class User(val name: String)
data class UserDto(val name: String)

fun box(): String {
    val ok: Result<User> = Result.Ok(User("Ada"))
    val err: Result<User> = Result.Err("no")
    val mapped = listOf(ok, err).map { it.transformInto<ResultDto<UserDto>>() }
    return if (mapped == listOf(ResultDto.Ok(UserDto("Ada")), ResultDto.Err("no", 400))) "OK" else "Fail: $mapped"
}
