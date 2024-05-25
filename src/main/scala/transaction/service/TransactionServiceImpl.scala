package transaction.service

import account.Account
import account.storage.{AccountNotFound, AccountStorage, ConcurrentModification}
import core.Amount
import transaction.{Internal, Withdrawal}
import transaction.storage._
import withdrawal.scala.WithdrawalService

class TransactionServiceImpl(withdrawalService: WithdrawalService, accountStorage: AccountStorage, transactionStorage: TransactionStorage) extends TransactionService {
  override def requestTransaction(transaction: Internal): Either[TransactionError, TransactionId] = {
    val result: Either[TransactionError, TransactionId] = for {
      from <- accountStorage.getAccount(transaction.from).left.map(AccountStorageFault)
      to <- accountStorage.getAccount(transaction.to).left.map(AccountStorageFault)

      reservedAmount <- reserveBalance(from, transaction.amount)

      transactionId <- transactionStorage.createTransaction(transaction).left.map {
        case TransactionWithIdAlreadyExists(existingTransaction) =>
          returnReservedBalance(from, reservedAmount)
          if (existingTransaction == transaction)
            TransactionStorageFault(TransactionWithIdAlreadyExists(existingTransaction))
          else
            IdempotencyViolation
      }

      _ <- accountStorage.addBalance(to.id, reservedAmount).left.map(AccountStorageFault)
    } yield transactionId

    result match {
      //Implementation of Idempotency
      case Left(TransactionStorageFault(TransactionWithIdAlreadyExists(_))) => Right(transaction.id)
      case other => other
    }
  }

  override def requestWithdrawal(withdrawal: Withdrawal): Either[TransactionError, TransactionId] = ???

  private def reserveBalance(account: Account, amount: Amount): Either[TransactionError, Amount] = {
    if (account.balance < amount) {
      Left(InsufficientFunds)
    } else {
      accountStorage.conditionalPutAccount(account.copy(balance = account.balance - amount)) match {
        case Left(error) => Left(AccountStorageFault(error))
        case Right(_) => Right(amount)
      }
    }
  }

  private def returnReservedBalance(account: Account, amount: Amount): Unit = {
    accountStorage.addBalance(account.id, amount)
  }
}
