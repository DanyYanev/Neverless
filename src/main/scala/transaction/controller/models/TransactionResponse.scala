package transaction.controller.models

import cats.effect.IO
import core.Amount
import io.circe.{Encoder, Json}
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import io.circe.syntax.EncoderOps
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf
import transaction.TransactionStatus
import transaction.service.models.{Internal, Transaction, Withdrawal}

import java.time.Instant
import java.util.UUID

sealed trait TransactionResponse {
  def id: UUID

  def accountId: UUID

  def amount: Amount

  def timestamp: Instant

  def transactionType: TransactionType
}

case class InternalTransactionResponse(
  id: UUID,
  accountId: UUID,
  toAccountId: UUID,
  amount: Amount,
  timestamp: Instant,
  status: TransactionStatus
) extends TransactionResponse {
  val transactionType: TransactionType = TransactionType.Internal
}

case class WithdrawalTransactionResponse(
  id: UUID,
  accountId: UUID,
  toAddress: String,
  amount: Amount,
  timestamp: Instant,
  status: TransactionStatus
) extends TransactionResponse {
  val transactionType: TransactionType = TransactionType.Withdrawal
}

object TransactionResponse {
  implicit val encodeTransactionResponse: Encoder[TransactionResponse] = Encoder.instance {
    case i: InternalTransactionResponse =>
      Json.obj(
        ("id", Json.fromString(i.id.toString)),
        ("accountId", Json.fromString(i.accountId.toString)),
        ("toAccountId", Json.fromString(i.toAccountId.toString)),
        ("amount", i.amount.asJson),
        ("timestamp", Json.fromString(i.timestamp.toString)),
        ("status", Json.fromString(i.status.value)),
        ("transactionType", Json.fromString(i.transactionType.value)),
      )
    case w: WithdrawalTransactionResponse =>
      Json.obj(
        ("id", Json.fromString(w.id.toString)),
        ("accountId", Json.fromString(w.accountId.toString)),
        ("toAddress", Json.fromString(w.toAddress)),
        ("amount", w.amount.asJson),
        ("timestamp", Json.fromString(w.timestamp.toString)),
        ("status", Json.fromString(w.status.value)),
        ("transactionType", Json.fromString(w.transactionType.value)),
      )
  }

  def fromTransaction(transaction: Transaction): TransactionResponse = transaction match {
    case Internal(id, from, to, amount, timestamp, status) => InternalTransactionResponse(id.value, from.value, to.value, amount, timestamp, status)
    case Withdrawal(id, _, from, to, amount, timestamp, status) => WithdrawalTransactionResponse(id.value, from.value, to.value, amount, timestamp, status)
  }

  implicit val transactionResponseListEncoder: Encoder[List[TransactionResponse]] = Encoder.encodeList(encodeTransactionResponse)
}

sealed trait TransactionType {
  def value: String
}

//Scala 2 doesn't have good built-in support for enums.
//This is a common workaround without importing an enum library
object TransactionType {
  case object Internal extends TransactionType {
    val value: String = "internal"
  }

  case object Withdrawal extends TransactionType {
    val value: String = "withdrawal"
  }

  implicit val encodeTransactionType: Encoder[TransactionType] = Encoder.encodeString.contramap[TransactionType](_.value)
}

case class InternalTransactionRequest(id: UUID, toAccountId: UUID, amount: Amount)

case class WithdrawalRequest(id: UUID, toAddress: String, amount: Amount)

object Requests {
  implicit val internalTransactionDecoder: EntityDecoder[IO, InternalTransactionRequest] = jsonOf[IO, InternalTransactionRequest]
  implicit val withdrawalTransactionDecoder: EntityDecoder[IO, WithdrawalRequest] = jsonOf[IO, WithdrawalRequest]
}
