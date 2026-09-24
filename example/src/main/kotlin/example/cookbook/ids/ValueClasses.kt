package example.cookbook.ids

import io.github.matthewjones372.kimney.transformInto

@JvmInline
value class UserId(val raw: Long)

@JvmInline
value class OwnerId(val value: Long)

data class User(val id: UserId, val name: String)

data class UserRow(val id: Long, val name: String)

data class Document(val owner: OwnerId)

fun toRow(user: User): UserRow = user.transformInto<UserRow>()

fun fromRow(row: UserRow): User = row.transformInto<User>()

fun ownerOf(user: User): OwnerId = user.id.transformInto<OwnerId>()
