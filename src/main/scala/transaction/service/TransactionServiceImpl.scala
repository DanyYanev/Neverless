package transaction.service

import account.storage.{AccountNotFound, AccountStorage}
import account.{Account, AccountId}
import core.{Address, Amount}
import transaction._
import transaction.service.models.Transaction
import transaction.storage._
import transaction.storage.models.{InternalRecord, TransactionRecord, WithdrawalRecord}
import utils.UUIDGenerator
import withdrawal.scala.{WithdrawalId, WithdrawalService, IdempotencyViolation => WithdrawalIdempotencyViolation}

import java.time.Instant

class TransactionServiceImpl(withdrawalService: WithdrawalService, accountStorage: AccountStorage, transactionStorage: TransactionStorage, generator: UUIDGenerator) extends TransactionService {
  override def requestTransaction(
    id: TransactionId,
    from: AccountId,
    to: AccountId,
    amount: Amount,
    timestamp: Instant
  ): Either[TransactionError, TransactionId] = {
    val result: Either[TransactionError, TransactionId] = for {
      from <- accountStorage.getAccount(from).left.map(AccountStorageFault)
      to <- accountStorage.getAccount(to).left.map(AccountStorageFault)

      // There is a huge problem here.
      // If the service crashes between reserving amount and creating transaction, the reserved amount will be lost.
      // This is a solved problem we just need to do all updates in a single transaction.
      // SQL Transactions, optimistic locking(1) + conditional updates on multiple rows, write-ahead logs or something else.
      // (1) Locking has to implement TTL else it can lock resources forever.
      // I've decided to bury my head into the sand and pretend that problem doesn't exists for sake of time.
      reservedAmount <- reserveBalance(from, amount)

      record = InternalRecord(id, from.id, to.id, amount, timestamp)
      transactionId <- transactionStorage.createTransaction(record).left.map {
        case TransactionWithIdAlreadyExists(existingTransaction) =>
          //Transaction has failed, return reserved amount to the account.
          returnReservedBalance(from, reservedAmount)
          if (existingTransaction == record)
            TransactionStorageFault(TransactionWithIdAlreadyExists(existingTransaction))
          else
            //If the transaction is a different one, return IdempotencyViolation.
            IdempotencyViolation
      }

      // We have successfully created the transaction, now we credit the receiving account.
      _ <- accountStorage.addBalance(to.id, reservedAmount).left.map(AccountStorageFault)
    } yield transactionId

    result match {
      //If the above code failed with TransactionWithIdAlreadyExists, return the transaction id. (Idempotency)
      case Left(TransactionStorageFault(TransactionWithIdAlreadyExists(_))) => Right(id)
      case other => other
    }
  }

  override def requestWithdrawal(
    id: TransactionId,
    from: AccountId,
    to: Address,
    amount: Amount,
    timestamp: Instant
  ): Either[TransactionError, TransactionId] = {
    val result = for {
      account <- accountStorage.getAccount(from).left.map(AccountStorageFault)

      //Same issue here as above, if the service stops during the critical zone, the reserved amount will be lost.
      reservedAmount <- reserveBalance(account, amount)

      withdrawal = WithdrawalRecord(id, WithdrawalId(generator.generateUUID()), from, to, amount, timestamp)

      transactionId <- transactionStorage.createTransaction(withdrawal).left.map {
        case TransactionWithIdAlreadyExists(existingWithdrawal) =>
          //Transaction has failed, return reserved amount to the account.
          returnReservedBalance(account, reservedAmount)
          existingWithdrawal match {
            case WithdrawalRecord(_, _, from, to, amount, _)
              if withdrawal.from == from &&
                withdrawal.to == to &&
                withdrawal.amount == amount =>
              //If the transaction is the same, return the transaction id. (Idempotency)
              TransactionStorageFault(TransactionWithIdAlreadyExists(existingWithdrawal))
            case _ => IdempotencyViolation
          }
      }

      _ <- withdrawalService.requestWithdrawal(withdrawal.withdrawalId, withdrawal.to, withdrawal.amount).left.map {
        case WithdrawalIdempotencyViolation() =>
          //The withdrawal service has failed to create the withdrawal, return the reserved amount to the account and revert the transaction.
          //This shouldn't happen as withdrawalId is freshly generated, but this service can be unavailable in reality and then we would have to revert previous actions anyway.
          returnReservedBalance(account, reservedAmount)
          transactionStorage.deleteTransaction(transactionId)
          IdempotencyViolation
      }
    } yield transactionId

    result match {
      //While trying to create a transaction, we found an identical transaction so we return the transaction id. (Idempotency)
      case Left(TransactionStorageFault(TransactionWithIdAlreadyExists(_))) => Right(id)
      case other => other
    }
  }


  override def getTransaction(accountId: AccountId, id: TransactionId): Option[Transaction] = {
    transactionStorage.getTransaction(id).filter(_.from == accountId).flatMap(withStatus)
  }

  override def getTransactionHistory(accountId: AccountId): Either[AccountNotFound, List[Transaction]] = {
    accountStorage
      .getAccount(accountId)
      .map(account => transactionStorage.getTransactions(account.id))
      .map(_.flatMap(withStatus))
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

  //So the code reads better.
  private def returnReservedBalance(account: Account, amount: Amount): Unit = {
    accountStorage.addBalance(account.id, amount)
  }

  private def withStatus(record: TransactionRecord): Option[Transaction] = record match {
    case InternalRecord(_, _, _, _, _) => Some(Transaction.from(record, TransactionStatus.Completed))
    case WithdrawalRecord(_, withdrawalId, _, _, _, _) =>
      withdrawalService.getWithdrawalStatus(withdrawalId).map(TransactionStatus.from).map(Transaction.from(record, _))
  }
}
