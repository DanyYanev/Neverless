package transaction.controller

import account.AccountId
import account.storage.AccountNotFound
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import core.Amount
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax.EncoderOps
import org.http4s._
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import transaction.controller.models.InternalTransactionRequest
import transaction.service._
import transaction.service.models.Internal
import transaction.{TransactionId, TransactionStatus}

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

class TransactionRecordControllerSpec extends AnyWordSpec with MockFactory with Http4sDsl[IO] {
  val transactionService: TransactionService = mock[TransactionService]
  val fixedClock: Clock = Clock.fixed(Instant.parse("2023-01-01T00:00:00Z"), ZoneOffset.UTC)
  val controller = new TransactionController(transactionService, fixedClock)
  val routes: HttpRoutes[IO] = controller.routes

  "TransactionController" when {
    "requested internal transaction is called" should {
      "return OK for a successful internal transaction" in {
        val accountId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val to = UUID.randomUUID()
        val req = InternalTransactionRequest(requestId, to, Amount(100))

        (transactionService.requestTransaction _).expects(
          TransactionId(requestId),
          AccountId(accountId),
          AccountId(to),
          req.amount,
          Instant.now(fixedClock)
        ).returning(Right(TransactionId(requestId)))

        val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions" / "internal")
          .withEntity(req.asJson)

        val response = routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.Ok
      }

      "return BadRequest for insufficient funds" in {
        val accountId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val to = UUID.randomUUID()
        val req = InternalTransactionRequest(requestId, to, Amount(100))

        (transactionService.requestTransaction _).expects(
          TransactionId(requestId),
          AccountId(accountId),
          AccountId(to),
          req.amount,
          Instant.now(fixedClock)
        ).returning(Left(InsufficientFunds))

        val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions" / "internal")
          .withEntity(req.asJson)

        val response = routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.BadRequest
      }

      "return Conflict for idempotency violation" in {
        val accountId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val to = UUID.randomUUID()
        val req = InternalTransactionRequest(requestId, to, Amount(100))

        (transactionService.requestTransaction _).expects(
          TransactionId(requestId),
          AccountId(accountId),
          AccountId(to),
          req.amount,
          Instant.now(fixedClock)
        ).returning(Left(IdempotencyViolation))

        val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions" / "internal")
          .withEntity(req.asJson)

        val response = routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.Conflict
      }

      "return NotFound for account not found" in {
        val accountId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val to = UUID.randomUUID()
        val req = InternalTransactionRequest(requestId, to, Amount(100))

        (transactionService.requestTransaction _).expects(
          TransactionId(requestId),
          AccountId(accountId),
          AccountId(to),
          req.amount,
          Instant.now(fixedClock)
        ).returning(Left(AccountStorageFault(AccountNotFound(AccountId(to)))))

        val request = Request[IO](Method.POST, uri"/accounts" / accountId.toString / "transactions" / "internal")
          .withEntity(req.asJson)

        val response = routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.NotFound
      }
    }
    "requested transaction by id" should {
      "return transaction" in {
        val id = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val transaction = Internal(
          TransactionId(id),
          AccountId(accountId),
          AccountId(UUID.randomUUID()),
          Amount(100),
          Instant.now(fixedClock),
          TransactionStatus.Completed
        )

        (transactionService.getTransaction _).expects(AccountId(accountId), TransactionId(id)).returning(Some(transaction))

        val request = Request[IO](Method.GET, uri"/accounts" / accountId.toString / "transactions" / id.toString)

        val response = routes.orNotFound.run(request).unsafeRunSync()

        val expectedJson =
          s"""
             |{
             |  "id": "${transaction.id.value}",
             |  "accountId": "${transaction.from.value}",
             |  "toAccountId": "${transaction.to.value}",
             |  "amount": {
             |    "value": ${transaction.amount.value}
             |  },
             |  "timestamp": "${transaction.timestamp}",
             |  "status": "completed",
             |  "transactionType": "internal"
             |}
             |""".stripMargin

        response.status shouldBe Status.Ok
        parse(response.as[String].unsafeRunSync()) shouldBe parse(expectedJson)
      }
      "return not found if transaction is missing" in {
        val id = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        (transactionService.getTransaction _).expects(AccountId(accountId), TransactionId(id)).returning(None)

        val request = Request[IO](Method.GET, uri"/accounts" / accountId.toString / "transactions" / id.toString)

        val response = routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.NotFound
      }
    }
    "requested transactions by accountId" should {
      //Similar to other tests :)
    }
  }
}
