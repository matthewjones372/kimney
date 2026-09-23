// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.into

open class Base(val inherited: String)
class User(val fullName: String, val born: Int, inherited: String) : Base(inherited)
data class UserDto(val name: String, val source: String, val age: Long) {
    val nickname: String get() = name
}

fun valid(user: User): UserDto = user.into<_, UserDto>()
    .withFieldRenamed(User::fullName, UserDto::name)
    .withFieldConst(UserDto::source, "import")
    .withFieldComputed(UserDto::age) { 2026L - it.born }
    .transform()

fun missing(user: User): UserDto = <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>()
    .withFieldRenamed(User::fullName, UserDto::name)
    .transform()<!>

fun notAParameter(user: User): UserDto = <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>()
    .withFieldRenamed(User::fullName, UserDto::name)
    .withFieldConst(UserDto::source, "import")
    .withFieldConst(UserDto::age, 1L)
    .withFieldConst(UserDto::nickname, "Ada")
    .transform()<!>

fun twice(user: User): UserDto = <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>()
    .withFieldRenamed(User::fullName, UserDto::name)
    .withFieldConst(UserDto::name, "Ada")
    .withFieldConst(UserDto::source, "import")
    .withFieldConst(UserDto::age, 1L)
    .transform()<!>

fun widened(user: User): UserDto = <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>()
    .withFieldRenamed(User::fullName, UserDto::name)
    .withFieldConst(UserDto::source, "import")
    .withFieldConst(UserDto::age, "forty")
    .transform()<!>

fun unreadable(user: User): UserDto = <!KIMNEY_CANNOT_TRANSFORM!>user.into<_, UserDto>()
    .withFieldRenamed(User::inherited, UserDto::name)
    .withFieldConst(UserDto::source, "import")
    .withFieldConst(UserDto::age, 1L)
    .transform()<!>
