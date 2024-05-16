package withdrawal.scala

import java.util.UUID

trait WithdrawalService {
  def requestWithdrawal(id: UUID, address: String, amount: Int): Either[String, Unit]
  def getWithdrawalStatus(id: UUID): Either[String, String]
}

