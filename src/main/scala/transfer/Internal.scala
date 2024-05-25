package transfer

import account.AccountId
import core.{Address, Amount}
import transfer.service.TransferId

// This is simply for storage conveniences
sealed trait Transaction {
  def id: TransferId

  def amount: Amount
}

case class Internal(id: TransferId, from: AccountId, to: AccountId, amount: Amount) extends Transaction

case class Withdrawal(id: TransferId, from: AccountId, to: Address, amount: Amount) extends Transaction
