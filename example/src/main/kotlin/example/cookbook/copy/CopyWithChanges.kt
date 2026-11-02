package example.cookbook.copy

import dev.surefold.kimney.into

data class Account(val id: Long, val owner: String, val suspended: Boolean)

fun Account.suspend(): Account = into<_, Account>()
    .withFieldConst(Account::suspended, true)
    .transform()
