package transaction

import account.AccountId
import core.{Address, Amount}
import withdrawal.scala.WithdrawalId

import java.util.UUID

case class TransactionId(value: UUID) extends AnyVal

// This is simply for storage conveniences
sealed trait Transaction {
  def id: TransactionId

  def amount: Amount
}

case class Internal(id: TransactionId, from: AccountId, to: AccountId, amount: Amount) extends Transaction

case class Withdrawal(id: TransactionId, withdrawalId: WithdrawalId, from: AccountId, to: Address, amount: Amount) extends Transaction