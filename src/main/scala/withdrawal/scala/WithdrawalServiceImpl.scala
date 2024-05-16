package withdrawal.scala

import withdrawal.java.{WithdrawalService => JavaWithdrawalService}
import withdrawal.java.WithdrawalService.{Address => JavaAddress, WithdrawalId => JavaWithdrawalId}

import java.util.UUID


class WithdrawalServiceImpl(javaService: JavaWithdrawalService) extends WithdrawalService {
  def requestWithdrawal(id: UUID, address: String, amount: Int): Either[String, Unit] = {
    try {
      javaService.requestWithdrawal(new JavaWithdrawalId(id), new JavaAddress(address), amount)
      Right(())
    } catch {
      case e: IllegalArgumentException => Left(e.getMessage)
    }
  }

  def getWithdrawalStatus(id: UUID): Either[String, String] = {
    try {
      Right(javaService.getRequestState(new JavaWithdrawalId(id))).map(_.toString)
    } catch {
      case e: IllegalArgumentException => Left(e.getMessage)
    }
  }
}

