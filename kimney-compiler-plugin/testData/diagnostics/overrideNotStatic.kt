// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.Into
import io.github.matthewjones372.kimney.into
import kotlin.reflect.KProperty1

data class User(val name: String)
data class UserDto(val name: String, val source: String)

fun stored(user: User): UserDto {
    val builder = <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>()<!>
    return <!KIMNEY_CANNOT_TRANSFORM!>builder.withFieldConst(UserDto::source, "import").transform()<!>
}

fun finish(chain: Into<User, UserDto>): UserDto = <!KIMNEY_CANNOT_TRANSFORM!>chain.transform()<!>

fun byVariable(user: User, field: KProperty1<UserDto, String>): UserDto =
    <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>().withFieldConst(field, "import").transform()<!>

fun byReference(user: User, source: (User) -> String): UserDto =
    <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>().withFieldComputed(UserDto::source, source).transform()<!>
