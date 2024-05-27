package transaction.controller

import account.AccountId
import account.storage.AccountNotFound
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import core.Amount
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import transaction.service._
import transaction.{Internal, TransactionId}

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

class TransactionControllerSpec extends AnyWordSpec with MockFactory with Http4sDsl[IO] {
  val transactionService: TransactionService = mock[TransactionService]
  val fixedClock: Clock = Clock.fixed(Instant.parse("2023-01-01T00:00:00Z"), ZoneOffset.UTC)
  val controller = new TransactionController(transactionService, fixedClock)
  val routes = controller.routes

  "TransactionController" should {

    "return OK for a successful internal transaction" in {
      val accountId = UUID.randomUUID()
      val requestId = UUID.randomUUID()
      val req = InternalTransactionRequest(requestId, UUID.randomUUID(), Amount(100))
      val transaction = Internal(
        id = TransactionId(requestId),
        from = AccountId(accountId),
        to = AccountId(req.toAccountId),
        amount = req.amount,
        timestamp = Instant.now(fixedClock)
      )

      (transactionService.requestTransaction _).expects(transaction).returning(Right(TransactionId(requestId)))

      val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions")
        .withEntity(req.asJson)

      val response = routes.orNotFound.run(request).unsafeRunSync()

      response.status shouldBe Status.Ok
    }

    "return BadRequest for insufficient funds" in {
      val accountId = UUID.randomUUID()
      val requestId = UUID.randomUUID()
      val req = InternalTransactionRequest(requestId, UUID.randomUUID(), Amount(100))
      val transaction = Internal(
        id = TransactionId(requestId),
        from = AccountId(accountId),
        to = AccountId(req.toAccountId),
        amount = req.amount,
        timestamp = Instant.now(fixedClock)
      )

      (transactionService.requestTransaction _).expects(transaction).returning(Left(InsufficientFunds))

      val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions")
        .withEntity(req.asJson)

      val response = routes.orNotFound.run(request).unsafeRunSync()

      response.status shouldBe Status.BadRequest
    }

    "return Conflict for idempotency violation" in {
      val accountId = UUID.randomUUID()
      val requestId = UUID.randomUUID()
      val req = InternalTransactionRequest(requestId, UUID.randomUUID(), Amount(100))
      val transaction = Internal(
        id = TransactionId(requestId),
        from = AccountId(accountId),
        to = AccountId(req.toAccountId),
        amount = req.amount,
        timestamp = Instant.now(fixedClock)
      )

      (transactionService.requestTransaction _).expects(transaction).returning(Left(IdempotencyViolation))

      val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions")
        .withEntity(req.asJson)

      val response = routes.orNotFound.run(request).unsafeRunSync()

      response.status shouldBe Status.Conflict
    }

    "return NotFound for account not found" in {
      val accountId = UUID.randomUUID()
      val requestId = UUID.randomUUID()
      val req = InternalTransactionRequest(requestId, UUID.randomUUID(), Amount(100))
      val transaction = Internal(
        id = TransactionId(requestId),
        from = AccountId(accountId),
        to = AccountId(req.toAccountId),
        amount = req.amount,
        timestamp = Instant.now(fixedClock)
      )

      (transactionService.requestTransaction _).expects(transaction).returning(Left(AccountStorageFault(AccountNotFound(AccountId(accountId)))))

      val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions")
        .withEntity(req.asJson)

      val response = routes.orNotFound.run(request).unsafeRunSync()

      response.status shouldBe Status.NotFound
    }
  }
}
