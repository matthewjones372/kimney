import io.github.matthewjones372.kimney.transformInto

@JvmInline value class Id<T>(val raw: Long)
@JvmInline value class Tagged<T>(val value: T)

data class User(val id: Id<User>, val nickname: Tagged<String>)
data class UserRow(val id: Long, val nickname: String)

fun box(): String {
    val row = User(Id(7), Tagged("ada")).transformInto<UserRow>()
    val back = row.transformInto<User>()
    return if (row == UserRow(7, "ada") && back == User(Id(7), Tagged("ada"))) "OK" else "Fail: $row, $back"
}
