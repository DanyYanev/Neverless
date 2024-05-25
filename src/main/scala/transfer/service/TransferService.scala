package transfer.service

import account.storage.AccountStorageError
import transfer.{Internal, Withdrawal}
import transfer.storage.TransferStorageError

import java.util.UUID

case class TransferId(value: UUID) extends AnyVal

sealed trait TransferError

case object InsufficientFunds extends TransferError

case class TransferStorageFault(err: TransferStorageError) extends TransferError

case class AccountStorageFault(err: AccountStorageError) extends TransferError

case object IdempotencyViolation extends TransferError

trait TransferService {
  def requestTransfer(transfer: Internal): Either[TransferError, TransferId]

  def requestWithdrawal(withdrawal: Withdrawal): Either[TransferError, TransferId]
}
