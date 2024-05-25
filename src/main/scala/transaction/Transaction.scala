package transaction

import account.AccountId
import core.{Address, Amount}
import transaction.service.TransactionId

// This is simply for storage conveniences
sealed trait Transaction {
  def id: TransactionId

  def amount: Amount
}

case class Internal(id: TransactionId, from: AccountId, to: AccountId, amount: Amount) extends Transaction

case class Withdrawal(id: TransactionId, from: AccountId, to: Address, amount: Amount) extends Transaction
