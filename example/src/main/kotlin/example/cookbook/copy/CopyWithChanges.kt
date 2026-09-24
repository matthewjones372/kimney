package example.cookbook.copy

import io.github.matthewjones372.kimney.into

data class Account(val id: Long, val owner: String, val suspended: Boolean)

fun suspend(account: Account): Account = account.into<_, Account>()
    .withFieldConst(Account::suspended, true)
    .transform()
