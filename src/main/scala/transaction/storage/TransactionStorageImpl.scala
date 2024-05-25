package transaction.storage

import transaction.Transaction
import transaction.service.TransactionId

import java.util.concurrent.ConcurrentHashMap

class TransactionStorageImpl extends TransactionStorage {

  private val transactions = new ConcurrentHashMap[TransactionId, Transaction]()

  override def getTransaction(id: TransactionId): Option[Transaction] = {
    Option(transactions.get(id))
  }

  override def createTransaction(transaction: Transaction): Either[TransactionWithIdAlreadyExists, TransactionId] = {
    getTransaction(transaction.id) match {
      case Some(existingTransaction) => Left(TransactionWithIdAlreadyExists(existingTransaction))
      case None =>
        transactions.put(transaction.id, transaction)
        Right(transaction.id)
    }
  }
}
