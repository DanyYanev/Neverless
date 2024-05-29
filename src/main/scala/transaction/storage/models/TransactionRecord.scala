package transaction.storage.models

import account.AccountId
import core.{Address, Amount}
import transaction.TransactionId
import withdrawal.scala.WithdrawalId

import java.time.Instant

sealed trait TransactionRecord {
  def id: TransactionId

  def from: AccountId

  def amount: Amount

  def timestamp: Instant
}

case class InternalRecord(id: TransactionId, from: AccountId, to: AccountId, amount: Amount, timestamp: Instant) extends TransactionRecord

case class WithdrawalRecord(id: TransactionId, withdrawalId: WithdrawalId, from: AccountId, to: Address, amount: Amount, timestamp: Instant) extends TransactionRecord
