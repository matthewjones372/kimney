package example.cookbook.ids

import io.github.matthewjones372.kimney.transformInto

@JvmInline
value class UserId(val raw: Long)

@JvmInline
value class OwnerId(val value: Long)

data class User(val id: UserId, val name: String)

data class UserRow(val id: Long, val name: String)

data class Document(val owner: OwnerId)

fun User.toRow(): UserRow = transformInto()

fun UserRow.toUser(): User = transformInto()

fun UserId.toOwnerId(): OwnerId = transformInto()
