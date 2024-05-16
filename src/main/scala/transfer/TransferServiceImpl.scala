package transfer

import core.amount.Amount
import withdrawal.scala.WithdrawalService

class TransferServiceImpl(withdrawalService: WithdrawalService) extends TransferService {
  override def transfer(id: TransferId, from: AccountId, to: AccountId, amount: Amount): Either[TransferError, TransferId] = ???

  override def withdraw(id: TransferId, from: AccountId, amount: Amount): Either[TransferError, TransferId] = ???
}
