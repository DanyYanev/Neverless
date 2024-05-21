package transfer

import core.{AccountId, Address, Amount}
import withdrawal.scala.{WithdrawalError}

import java.util.UUID

case class TransferId(value: UUID) extends AnyVal

sealed trait TransferError
case class WithdrawalFailure(error: WithdrawalError) extends TransferError
case object InsufficientFunds extends TransferError

trait TransferService {
  def requestTransfer(id: TransferId, from: AccountId, to: AccountId, amount: Amount): Either[TransferError, TransferId]
  def requestWithdrawal(id: TransferId, from: AccountId, to: Address, amount: Amount): Either[TransferError, TransferId]
}
