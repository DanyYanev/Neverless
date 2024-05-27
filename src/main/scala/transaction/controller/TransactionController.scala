package transaction.controller

import account.AccountId
import account.storage.{AccountNotFound, ConcurrentModification}
import cats.effect.IO
import core.{Address, Amount}
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import transaction.service.{AccountStorageFault, IdempotencyViolation, InsufficientFunds, TransactionError, TransactionService, TransactionStorageFault, WithdrawalRequest => WithdrawalServiceRequest}
import transaction.storage.TransactionWithIdAlreadyExists
import transaction.{Internal, TransactionId}

import java.time.{Clock, Instant}
import java.util.UUID

case class InternalTransactionRequest(id: UUID, toAccountId: UUID, amount: Amount)

case class WithdrawalRequest(id: UUID, toAddress: String, amount: Amount)

class TransactionController(transactionService: TransactionService, clock: Clock) {

  implicit val internalTransactionDecoder: EntityDecoder[IO, InternalTransactionRequest] = jsonOf[IO, InternalTransactionRequest]
  implicit val withdrawalTransactionDecoder: EntityDecoder[IO, WithdrawalRequest] = jsonOf[IO, WithdrawalRequest]

  private def requestInternalTransaction(accountId: UUID, req: InternalTransactionRequest): IO[Response[IO]] = {
    val transaction = Internal(
      id = TransactionId(req.id),
      from = AccountId(accountId),
      to = AccountId(req.toAccountId),
      amount = req.amount,
      timestamp = Instant.now(clock)
    )
    transactionService.requestTransaction(transaction) match {
      case Right(_) => Ok()
      case Left(error) => TransactionController.toCode(error)
    }
  }

  private def requestWithdrawal(accountId: UUID, req: WithdrawalRequest): IO[Response[IO]] = {
    val withdrawal = WithdrawalServiceRequest(
      id = TransactionId(req.id),
      from = AccountId(accountId),
      to = Address(req.toAddress),
      amount = req.amount,
      timestamp = Instant.now(clock)
    )
    transactionService.requestWithdrawal(withdrawal) match {
      case Right(_) => Ok()
      case Left(error) => TransactionController.toCode(error)
    }
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@POST -> Root / "accounts" / UUIDVar(accountId) / "transactions" =>
      req.decode[InternalTransactionRequest](requestInternalTransaction(accountId, _))

    case req@POST -> Root / "accounts" / UUIDVar(accountId) / "withdrawals" =>
      req.decode[WithdrawalRequest](requestWithdrawal(accountId, _))
  }
}

private object TransactionController {
  //This is where we do custom error formatting
  private def toCode(error: TransactionError): IO[Response[IO]] = {
    val status = error match {
      case InsufficientFunds => Status.BadRequest
      case IdempotencyViolation => Status.Conflict
      case AccountStorageFault(fault) => fault match {
        case ConcurrentModification(_) => Status.Conflict
        case AccountNotFound(_) => Status.NotFound
      }
      case TransactionStorageFault(fault) => fault match {
        //This is not great... That error should never be returned by the service layer (idempotency)
        case TransactionWithIdAlreadyExists(_) => Status.InternalServerError
      }
    }

    IO.pure(Response[IO](status = status))
  }
}

