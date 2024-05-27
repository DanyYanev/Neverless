package transaction.service

import account.AccountId
import account.storage.AccountStorageError
import core.{Address, Amount}
import transaction.storage.TransactionStorageError
import transaction.{Internal, TransactionId, TransactionStatus}

import java.time.Instant


sealed trait TransactionError

case object InsufficientFunds extends TransactionError

case class TransactionStorageFault(err: TransactionStorageError) extends TransactionError

case class AccountStorageFault(err: AccountStorageError) extends TransactionError

case object IdempotencyViolation extends TransactionError

case class WithdrawalRequest(id: TransactionId, from: AccountId, to: Address, amount: Amount, timestamp: Instant)

trait TransactionService {
  def requestTransaction(transaction: Internal): Either[TransactionError, TransactionId]

  def requestWithdrawal(withdrawal: WithdrawalRequest): Either[TransactionError, TransactionId]

  def getTransactionStatus(id: TransactionId): Option[TransactionStatus]
}
