package withdrawal.scala

import withdrawal.java.WithdrawalService.{WithdrawalState => JavaWithdrawalState}

sealed trait WithdrawalStatus

case object Processing extends WithdrawalStatus

case object Completed extends WithdrawalStatus

case object Failed extends WithdrawalStatus

object WithdrawalStatusConverter {
  //Unfortunately Java enums do not support exhaustive matching.
  //This provides protection against future changes in the Java enum.
  def convert(state: JavaWithdrawalState): WithdrawalStatus = state match {
    case JavaWithdrawalState.PROCESSING => Processing
    case JavaWithdrawalState.COMPLETED => Completed
    case JavaWithdrawalState.FAILED => Failed
  }
}