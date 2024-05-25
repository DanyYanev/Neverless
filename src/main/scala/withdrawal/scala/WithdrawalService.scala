package withdrawal.scala

import core.{Address, Amount}

import java.util.UUID
import withdrawal.java.WithdrawalService.{WithdrawalState => JavaWithdrawalState}

case class WithdrawalId private(value: UUID) extends AnyVal

sealed trait WithdrawalError

case object NotFound extends WithdrawalError

//A bit technical name, but idempotency is part of the API
case class IdempotencyViolation() extends WithdrawalError

case class UnknownStatus(status: JavaWithdrawalState) extends WithdrawalError

trait WithdrawalService {
  def requestWithdrawal(id: WithdrawalId, address: Address, amount: Amount): Either[IdempotencyViolation, WithdrawalId]

  def getWithdrawalStatus(id: WithdrawalId): Either[WithdrawalError, WithdrawalStatus]
}
