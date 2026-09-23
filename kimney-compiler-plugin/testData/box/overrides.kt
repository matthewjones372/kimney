import io.github.matthewjones372.kimney.into

data class User(val fullName: String, val born: Int, val email: String)
data class UserDto(val name: String, val source: String, val age: Int, val email: String)

fun box(): String {
    val now = 2026
    val dto = User("Ada Lovelace", 1815, "ada@example.com").into<_, UserDto>()
        .withFieldRenamed(User::fullName, UserDto::name)
        .withFieldConst(UserDto::source, "import")
        .withFieldComputed(UserDto::age) { now - it.born }
        .transform()
    val expected = UserDto("Ada Lovelace", "import", 211, "ada@example.com")
    return if (dto == expected) "OK" else "Fail: $dto"
}
