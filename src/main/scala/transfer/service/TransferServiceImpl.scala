package transfer.service

import account.Account
import account.storage.{AccountNotFound, AccountStorage, ConcurrentModification}
import core.Amount
import transfer.{Internal, Withdrawal}
import transfer.storage._
import withdrawal.scala.WithdrawalService

class TransferServiceImpl(withdrawalService: WithdrawalService, accountStorage: AccountStorage, transferStorage: TransferStorage) extends TransferService {
  override def requestTransfer(transfer: Internal): Either[TransferError, TransferId] = {
    val result: Either[TransferError, TransferId] = for {
      from <- accountStorage.getAccount(transfer.from).left.map(AccountStorageFault)
      to <- accountStorage.getAccount(transfer.to).left.map(AccountStorageFault)

      reservedAmount <- reserveBalance(from, transfer.amount)

      transferId <- transferStorage.createTransfer(transfer).left.map {
        case TransferWithIdAlreadyExists(existingTransfer) =>
          returnReservedBalance(from, reservedAmount)
          if (existingTransfer == transfer)
            TransferStorageFault(TransferWithIdAlreadyExists(existingTransfer))
          else
            IdempotencyViolation
      }

      _ <- accountStorage.addBalance(to.id, reservedAmount).left.map(AccountStorageFault)
    } yield transferId

    result match {
      //Implementation of Idempotency
      case Left(TransferStorageFault(TransferWithIdAlreadyExists(_))) => Right(transfer.id)
      case other => other
    }
  }

  override def requestWithdrawal(withdrawal: Withdrawal): Either[TransferError, TransferId] = ???

  private def reserveBalance(account: Account, amount: Amount): Either[TransferError, Amount] = {
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
