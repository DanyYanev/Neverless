package transfer

import core.{AccountId, Address, Amount}
import withdrawal.scala.{WithdrawalError, WithdrawalId, WithdrawalService}

class TransferServiceImpl(withdrawalService: WithdrawalService) extends TransferService {

  override def requestTransfer(id: TransferId, from: AccountId, to: AccountId, amount: Amount): Either[TransferError, TransferId] = ???

  override def requestWithdrawal(id: TransferId, from: AccountId, to: Address, amount: Amount): Either[TransferError, TransferId] = {
    withdrawalService.requestWithdrawal(WithdrawalId(id.value), to, amount) match {
      case Right(withdrawalId) => Right(TransferId(withdrawalId.value))
      case Left(error) => Left(WithdrawalFailure(error))
    }
  }
}
