package transaction

import account.AccountId
import core.{Address, Amount}
import withdrawal.scala.{WithdrawalId, WithdrawalStatus}

import java.time.Instant
import java.util.UUID

case class TransactionId(value: UUID) extends AnyVal

// This is simply for storage conveniences
sealed trait Transaction {
  def id: TransactionId

  def from: AccountId

  def amount: Amount

  def timestamp: Instant
}

case class Internal(id: TransactionId, from: AccountId, to: AccountId, amount: Amount, timestamp: Instant) extends Transaction

case class Withdrawal(id: TransactionId, withdrawalId: WithdrawalId, from: AccountId, to: Address, amount: Amount, timestamp: Instant) extends Transaction

sealed trait TransactionStatus

case object Processing extends TransactionStatus

case object Completed extends TransactionStatus

case object Failed extends TransactionStatus

object TransactionStatus {
  def from(status: WithdrawalStatus): TransactionStatus = status match {
    case withdrawal.scala.Processing => Processing
    case withdrawal.scala.Completed => Completed
    case withdrawal.scala.Failed => Failed
  }
}