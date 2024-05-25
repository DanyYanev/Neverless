package withdrawal.scala

import core.{Address, Amount}
import withdrawal.java.{WithdrawalService => JavaWithdrawalService}
import withdrawal.java.WithdrawalService.{Address => JavaAddress, WithdrawalId => JavaWithdrawalId}

class WithdrawalServiceImpl(javaService: JavaWithdrawalService) extends WithdrawalService {
  def requestWithdrawal(id: WithdrawalId, address: Address, amount: Amount): Either[IdempotencyViolation, WithdrawalId] = {
    try {
      javaService.requestWithdrawal(new JavaWithdrawalId(id.value), new JavaAddress(address.value), amount)
      Right(id)
    } catch {
      case _: IllegalArgumentException => Left(IdempotencyViolation())
    }
  }

  def getWithdrawalStatus(id: WithdrawalId): Either[WithdrawalError, WithdrawalStatus] = {
    try {
      val status = javaService.getRequestState(new JavaWithdrawalId(id.value))
      WithdrawalStatusConverter.convert(status)
    } catch {
      case _: IllegalArgumentException => Left(NotFound)
    }
  }
}

