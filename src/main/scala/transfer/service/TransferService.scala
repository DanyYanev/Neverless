package transfer

import account.AccountId
import account.storage.AccountStorageError
import core.{Address, Amount}
import transfer.storage.TransferStorageError
import withdrawal.scala.WithdrawalError

import java.util.UUID

case class TransferId(value: UUID) extends AnyVal

sealed trait TransferError

case object InsufficientFunds extends TransferError

case class TransferStorageFault(err: TransferStorageError) extends TransferError

case class AccountStorageFault(err: AccountStorageError) extends TransferError

case object IdempotencyViolation extends TransferError

trait TransferService {
  def requestTransfer(transfer: Transfer): Either[TransferError, TransferId]

  def requestWithdrawal(withdrawal: Withdrawal): Either[TransferError, TransferId]
}
