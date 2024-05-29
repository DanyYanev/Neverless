package transaction

import withdrawal.scala.WithdrawalStatus

sealed trait TransactionStatus {
  def value: String
}

object TransactionStatus {
  case object Processing extends TransactionStatus {
    val value: String = "processing"
  }

  case object Completed extends TransactionStatus {
    val value: String = "completed"
  }

  case object Failed extends TransactionStatus {
    val value: String = "failed"
  }

  def from(status: WithdrawalStatus): TransactionStatus = status match {
    case withdrawal.scala.Processing => Processing
    case withdrawal.scala.Completed => Completed
    case withdrawal.scala.Failed => Failed
  }
}