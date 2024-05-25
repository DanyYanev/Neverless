package transaction.storage

import transaction.Transaction
import transaction.service.TransactionId

class TransactionStorageStub(var transactions: Map[TransactionId, Transaction] = Map.empty) extends TransactionStorage {
  override def getTransaction(id: TransactionId): Option[Transaction] = transactions.get(id)

  override def createTransaction(transaction: Transaction): Either[TransactionWithIdAlreadyExists, TransactionId] = {
    transactions.get(transaction.id) match {
      case Some(existingTransaction) => Left(TransactionWithIdAlreadyExists(existingTransaction))
      case None =>
        transactions = transactions.updated(transaction.id, transaction)
        Right(transaction.id)
    }
  }
}
