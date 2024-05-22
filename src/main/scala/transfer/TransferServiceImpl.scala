package transfer

import core.Amount
import transfer.storage._
import withdrawal.scala.WithdrawalService

class TransferServiceImpl(withdrawalService: WithdrawalService, accountStorage: AccountStorage, transferStorage: TransferStorage) extends TransferService {
  override def requestTransfer(transfer: Transfer): Either[TransferError, TransferId] = {
    (for {
      from <- accountStorage.getAccount(transfer.from).toRight(AccountNotFound(transfer.from))
      to <- accountStorage.getAccount(transfer.to).toRight(AccountNotFound(transfer.to))

      reservedAmount <- reserveBalance(from, transfer.amount)

      transferId <- transferStorage.createTransfer(transfer).left.map {
        case TransferWithIdAlreadyExists(existingTransfer) =>
          returnReservedBalance(from, reservedAmount)
          if (existingTransfer == transfer)
            TransferAlreadyExists
          else
            IdempotencyViolation
      }

      _ <- accountStorage.addBalance(to.id, reservedAmount).left.map(_ => AccountNotFound(transfer.to))
    } yield transferId) match {
      //Implementation of Idempotency
      case Left(TransferAlreadyExists) => Right(transfer.id)
      case other => other
    }
  }

  override def requestWithdrawal(withdrawal: Withdrawal): Either[TransferError, TransferId] = ???

  private def reserveBalance(account: Account, amount: Amount): Either[TransferError, Amount] = {
    if (account.balance < amount) {
      Left(InsufficientFunds)
    } else {
      accountStorage.conditionalPutAccount(account.copy(balance = account.balance - amount)) match {
        case Left(ConcurrentModificationError) => Left(IdempotencyViolation)
        case Right(_) => Right(amount)
      }
    }
  }

  private def returnReservedBalance(account: Account, amount: Amount): Unit = {
    accountStorage.addBalance(account.id, amount)
  }
}
