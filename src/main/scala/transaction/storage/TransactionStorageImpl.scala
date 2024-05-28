package transaction.storage

import account.AccountId
import transaction.{Transaction, TransactionId}
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap

class TransactionStorageImpl extends TransactionStorage {

  private val transactions = new ConcurrentHashMap[TransactionId, Transaction]()

  override def getTransaction(id: TransactionId): Option[Transaction] = {
    Option(transactions.get(id))
  }

  override def getTransactions(id: AccountId): List[Transaction] = {
    transactions.values().asScala.toList.filter(_.from == id)
  }

  override def createTransaction(transaction: Transaction): Either[TransactionWithIdAlreadyExists, TransactionId] = {
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
