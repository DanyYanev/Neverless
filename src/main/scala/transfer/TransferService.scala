package transfer

import account.AccountId
import core.{Address, Amount}
import transfer.storage.{Transfer, Withdrawal}
import withdrawal.scala.WithdrawalError

import java.util.UUID

case class TransferId(value: UUID) extends AnyVal

sealed trait TransferError

case object InsufficientFunds extends TransferError

case class AccountNotFound(id: AccountId) extends TransferError

case object ConcurrentModification extends TransferError

case object IdempotencyViolation extends TransferError

case object TransferAlreadyExists extends TransferError

trait TransferService {
  def requestTransfer(transfer: Transfer): Either[TransferError, TransferId]

  def requestWithdrawal(withdrawal: Withdrawal): Either[TransferError, TransferId]
}
