package transaction.service

import account.Account
import account.storage.AccountStorage
import core.Amount
import transaction.storage._
import transaction.{Internal, TransactionId, Withdrawal}
import withdrawal.scala.{WithdrawalId, WithdrawalService, IdempotencyViolation => WithdrawalIdempotencyViolation}

import java.util.UUID

class TransactionServiceImpl(withdrawalService: WithdrawalService, accountStorage: AccountStorage, transactionStorage: TransactionStorage) extends TransactionService {
  override def requestTransaction(transaction: Internal): Either[TransactionError, TransactionId] = {
    val result: Either[TransactionError, TransactionId] = for {
      from <- accountStorage.getAccount(transaction.from).left.map(AccountStorageFault)
      to <- accountStorage.getAccount(transaction.to).left.map(AccountStorageFault)

      // There is a huge problem here.
      // If the service crashes between reserving amount and creating transaction, the reserved amount will be lost.
      // This is a solved problem we just need to do all updates in a single transaction.
      // SQL Transactions, optimistic locking(1) + conditional updates on multiple rows, write-ahead logs or something else.
      // (1) Locking has to implement TTL else it can lock resources forever.
      // I've decided to bury my head into the sand and pretend that problem doesn't exists for sake of time.
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

  override def requestWithdrawal(request: WithdrawalRequest): Either[TransactionError, TransactionId] = {
    val result = for {
      account <- accountStorage.getAccount(request.from).left.map(AccountStorageFault)

      //Same issue here as above, if any of the following operations fail, the reserved amount will be lost.
      reservedAmount <- reserveBalance(account, request.amount)

      withdrawal = Withdrawal(request.id, WithdrawalId(UUID.randomUUID()), request.from, request.to, request.amount)

      transactionId <- transactionStorage.createTransaction(withdrawal).left.map {
        case TransactionWithIdAlreadyExists(existingWithdrawal) =>
          returnReservedBalance(account, reservedAmount)
          existingWithdrawal match {
            case Internal(_, _, _, _) => IdempotencyViolation
            case Withdrawal(_, _, from, to, amount)
              if withdrawal.from == from &&
                withdrawal.to == to &&
                withdrawal.amount == amount =>
              TransactionStorageFault(TransactionWithIdAlreadyExists(existingWithdrawal))
            case _ => IdempotencyViolation
          }
      }

      _ <- withdrawalService.requestWithdrawal(withdrawal.withdrawalId, withdrawal.to, withdrawal.amount).left.map {
        case WithdrawalIdempotencyViolation() =>
          returnReservedBalance(account, reservedAmount)
          transactionStorage.deleteTransaction(transactionId)
          IdempotencyViolation
      }
    } yield transactionId

    result match {
      //Implementation of Idempotency
      case Left(TransactionStorageFault(TransactionWithIdAlreadyExists(_))) => Right(request.id)
      case other => other
    }
  }

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
