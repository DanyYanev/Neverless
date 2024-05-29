package transaction.service

import account.AccountId
import account.storage.{AccountNotFound, AccountStorageError}
import core.{Address, Amount}
import transaction.TransactionId
import transaction.service.models.Transaction
import transaction.storage.TransactionStorageError

import java.time.Instant


sealed trait TransactionError

case object InsufficientFunds extends TransactionError

case class TransactionStorageFault(err: TransactionStorageError) extends TransactionError

case class AccountStorageFault(err: AccountStorageError) extends TransactionError

case object IdempotencyViolation extends TransactionError

trait TransactionService {
  def requestTransaction(
    id: TransactionId,
    from: AccountId,
    to: AccountId,
    amount: Amount,
    timestamp: Instant
  ): Either[TransactionError, TransactionId]

  def requestWithdrawal(
    id: TransactionId,
    from: AccountId,
    to: Address,
    amount: Amount,
    timestamp: Instant
  ): Either[TransactionError, TransactionId]

  def getTransaction(accountId: AccountId, id: TransactionId): Option[Transaction]

  def getTransactionHistory(accountId: AccountId): Either[AccountNotFound, List[Transaction]]
}
