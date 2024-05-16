package withdrawal.scala

import java.util.UUID

import withdrawal.java.WithdrawalService.{WithdrawalState => JavaWithdrawalState}

class WithdrawalId private(val value: UUID) extends AnyVal
class Address private(val value: String) extends AnyVal

sealed trait WithdrawalError
case object NotFound extends WithdrawalError
//A bit technical name, but idempotency is part of the API
case object IdempotencyViolation extends WithdrawalError
case class UnknownStatus(status: JavaWithdrawalState) extends WithdrawalError


trait WithdrawalService {
  def requestWithdrawal(id: WithdrawalId, address: Address, amount: Int): Either[WithdrawalError, WithdrawalId]
  def getWithdrawalStatus(id: WithdrawalId): Either[WithdrawalError, WithdrawalStatus]
}

sealed trait WithdrawalStatus
case object Processing extends WithdrawalStatus
case object Completed extends WithdrawalStatus
case object Failed extends WithdrawalStatus

object WithdrawalStatusConverter {
  //Unfortunately Java enums do not support exhaustive matching.
  //This provides protection against future changes in the Java enum.
  def convert(state: JavaWithdrawalState): Either[UnknownStatus, WithdrawalStatus] = state match {
    case JavaWithdrawalState.PROCESSING => Right(Processing)
    case JavaWithdrawalState.COMPLETED  => Right(Completed)
    case JavaWithdrawalState.FAILED     => Right(Failed)
    case status: JavaWithdrawalState   => Left(UnknownStatus(status))
  }
}