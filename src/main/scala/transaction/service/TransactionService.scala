package transaction.service

import account.storage.AccountStorageError
import transaction.{Internal, Withdrawal}
import transaction.storage.TransactionStorageError

import java.util.UUID

case class TransactionId(value: UUID) extends AnyVal

sealed trait TransactionError

case object InsufficientFunds extends TransactionError

case class TransactionStorageFault(err: TransactionStorageError) extends TransactionError

case class AccountStorageFault(err: AccountStorageError) extends TransactionError

case object IdempotencyViolation extends TransactionError

trait TransactionService {
  def requestTransaction(transaction: Internal): Either[TransactionError, TransactionId]

  def requestWithdrawal(withdrawal: Withdrawal): Either[TransactionError, TransactionId]
}
