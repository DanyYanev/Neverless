package transaction.controller.models

import core.Amount
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import io.circe.{Decoder, Encoder}
import transaction.{Internal, Transaction, Withdrawal}

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
) extends TransactionResponse {
  val transactionType: TransactionType = TransactionType.Internal
}

case class WithdrawalTransactionResponse(
  id: UUID,
  accountId: UUID,
  toAddress: String,
  amount: Amount,
  timestamp: Instant,
) extends TransactionResponse {
  val transactionType: TransactionType = TransactionType.Withdrawal
}

object TransactionResponse {
  implicit val encodeTransactionResponse: Encoder[TransactionResponse] = Encoder.instance {
    case i: InternalTransactionResponse => Encoder[InternalTransactionResponse].apply(i)
    case w: WithdrawalTransactionResponse => Encoder[WithdrawalTransactionResponse].apply(w)
  }

  def fromTransaction(transaction: Transaction): TransactionResponse = transaction match {
    case Internal(id, from, to, amount, timestamp) => InternalTransactionResponse(id.value, from.value, to.value, amount, timestamp)
    case Withdrawal(id, _, from, to, amount, timestamp) => WithdrawalTransactionResponse(id.value, from.value, to.value, amount, timestamp)
  }
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

  //This is horrendous...
  //We can use reflection to populate this automatically
  val values: List[TransactionType] = List(Internal, Withdrawal)

  private def fromString(s: String): Option[TransactionType] = values.find(_.value == s)

  implicit val encodeTransactionType: Encoder[TransactionType] = Encoder.encodeString.contramap[TransactionType](_.value)
  implicit val decodeTransactionType: Decoder[TransactionType] = Decoder.decodeString.emap { str =>
    fromString(str).toRight(s"Invalid transaction type: $str")
  }
}