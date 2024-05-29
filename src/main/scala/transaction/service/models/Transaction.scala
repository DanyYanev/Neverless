package transaction.service.models

import account.AccountId
import core.{Address, Amount}
import transaction.storage.models.{InternalRecord, TransactionRecord, WithdrawalRecord}
import transaction.{TransactionId, TransactionStatus}
import withdrawal.scala.WithdrawalId

import java.time.Instant

sealed trait Transaction {
  def id: TransactionId

  def from: AccountId

  def amount: Amount

  def timestamp: Instant

  def status: TransactionStatus
}

case class Internal(
  id: TransactionId,
  from: AccountId,
  to: AccountId,
  amount: Amount,
  timestamp: Instant,
  status: TransactionStatus
) extends Transaction

case class Withdrawal(
  id: TransactionId,
  withdrawalId: WithdrawalId,
  from: AccountId,
  to: Address,
  amount: Amount,
  timestamp: Instant,
  status: TransactionStatus
) extends Transaction

object Transaction {
  def from(transaction: TransactionRecord, status: TransactionStatus): Transaction = transaction match {
    case InternalRecord(id, from, to, amount, timestamp) =>
      Internal(id, from, to, amount, timestamp, status)
    case WithdrawalRecord(id, withdrawalId, from, to, amount, timestamp) =>
      Withdrawal(id, withdrawalId, from, to, amount, timestamp, status)
  }
}
