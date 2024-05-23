package transfer

import account.AccountId
import core.{Address, Amount}

case class Transfer(id: TransferId, from: AccountId, to: AccountId, amount: Amount)

case class Withdrawal(id: TransferId, from: AccountId, to: Address, amount: Amount)
