package transaction.storage

import account.AccountId
import transaction.TransactionId
import transaction.storage.models.TransactionRecord

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class TransactionStorageImpl extends TransactionStorage {

  private val transactions = new ConcurrentHashMap[TransactionId, TransactionRecord]()

  override def getTransaction(id: TransactionId): Option[TransactionRecord] = {
    Option(transactions.get(id))
  }

  override def getTransactions(id: AccountId): List[TransactionRecord] = {
    transactions.values().asScala.toList.filter(_.from == id)
  }

  override def createTransaction(transaction: TransactionRecord): Either[TransactionWithIdAlreadyExists, TransactionId] = {
    getTransaction(transaction.id) match {
      case Some(existingTransaction) => Left(TransactionWithIdAlreadyExists(existingTransaction))
      case None =>
        transactions.put(transaction.id, transaction)
        Right(transaction.id)
    }
  }

  override def deleteTransaction(id: TransactionId): Option[TransactionId] = {
    Option(transactions.remove(id)).map(_ => id)
  }
}
