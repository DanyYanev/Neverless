package transaction.controller

import account.AccountId
import account.storage.{AccountNotFound, ConcurrentModification}
import cats.effect.IO
import core.Address
import io.circe.syntax.EncoderOps
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import transaction.TransactionId
import transaction.controller.models.Requests._
import transaction.controller.models.TransactionResponse.transactionResponseListEncoder
import transaction.controller.models.{InternalTransactionRequest, TransactionResponse, WithdrawalRequest}
import transaction.service._
import transaction.storage.TransactionWithIdAlreadyExists

import java.time.{Clock, Instant}
import java.util.UUID


class TransactionController(transactionService: TransactionService, clock: Clock) {


  private def requestInternalTransaction(accountId: UUID, req: InternalTransactionRequest): IO[Response[IO]] = {
    transactionService.requestTransaction(
      TransactionId(req.id),
      AccountId(accountId),
      AccountId(req.toAccountId),
      req.amount,
      Instant.now(clock)
    ) match {
      case Right(_) => Ok()
      case Left(error) => TransactionController.toCode(error)
    }
  }

  private def requestWithdrawal(accountId: UUID, req: WithdrawalRequest): IO[Response[IO]] = {
    transactionService.requestWithdrawal(
      TransactionId(req.id),
      AccountId(accountId),
      Address(req.toAddress),
      req.amount,
      Instant.now(clock)
    ) match {
      case Right(_) => Ok()
      case Left(error) => TransactionController.toCode(error)
    }
  }

  private def getAllTransactions(accountId: UUID): IO[Response[IO]] = {
    transactionService.getTransactionHistory(AccountId(accountId)) match {
      case Right(transactions) =>
        Ok(transactions.map(TransactionResponse.fromTransaction).asJson)
      case Left(AccountNotFound(_)) =>
        NotFound()
    }
  }

  private def getTransaction(accountId: UUID, transactionId: UUID): IO[Response[IO]] = {
    transactionService.getTransaction(AccountId(accountId), TransactionId(transactionId)) match {
      case Some(transaction) => Ok(TransactionResponse.fromTransaction(transaction).asJson)
      case None => NotFound()
    }
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@POST -> Root / "accounts" / UUIDVar(accountId) / "transactions" / "internal" =>
      req.decode[InternalTransactionRequest](requestInternalTransaction(accountId, _))

    case req@POST -> Root / "accounts" / UUIDVar(accountId) / "transactions" / "withdrawal" =>
      req.decode[WithdrawalRequest](requestWithdrawal(accountId, _))

    case GET -> Root / "accounts" / UUIDVar(accountId) / "transactions" =>
      getAllTransactions(accountId)

    case GET -> Root / "accounts" / UUIDVar(accountId) / "transactions" / UUIDVar(transactionId) =>
      getTransaction(accountId, transactionId)
  }
}

private object TransactionController {
  //This is where we do custom error formatting
  private def toCode(error: TransactionError): IO[Response[IO]] = {
    Console.println("Error: " + error)
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

