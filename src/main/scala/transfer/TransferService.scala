package transfer

import core.amount.Amount

import java.util.UUID

class TransferId private(val value: UUID) extends AnyVal
class AccountId private(val value: UUID) extends AnyVal

sealed trait TransferError
case class WithdrawalError(error: WithdrawalError) extends TransferError
case object InsufficientFunds extends TransferError

trait TransferService {
  def transfer(id: TransferId, from: AccountId, to: AccountId, amount: Amount): Either[TransferError, TransferId]
  def withdraw(id: TransferId, from: AccountId, amount: Amount): Either[TransferError, TransferId]
}
