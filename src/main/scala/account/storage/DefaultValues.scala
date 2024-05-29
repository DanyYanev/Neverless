package account.storage

import account.{Account, AccountId}
import core.Amount

import java.util.UUID

object DefaultValues {

  val accounts: List[Account] = List(
    Account(AccountId(UUID.fromString("00000000-0000-0000-0000-000000000001")), Amount(1000)),
    Account(AccountId(UUID.fromString("00000000-0000-0000-0000-000000000002")), Amount(100)),
  )
}
