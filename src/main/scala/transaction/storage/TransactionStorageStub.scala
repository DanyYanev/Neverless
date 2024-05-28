package transaction.storage

import account.AccountId
import transaction.{Transaction, TransactionId}

class TransactionStorageStub(var transactions: Map[TransactionId, Transaction] = Map.empty) extends TransactionStorage {
  override def getTransaction(id: TransactionId): Option[Transaction] = transactions.get(id)

  override def getTransactions(id: AccountId): List[Transaction] = {
    transactions.values.filter(_.from == id).toList
  }

  override def createTransaction(transaction: Transaction): Either[TransactionWithIdAlreadyExists, TransactionId] = {
    transactions.get(transaction.id) match {
      case Some(existingTransaction) => Left(TransactionWithIdAlreadyExists(existingTransaction))
      case None =>
        transactions = transactions.updated(transaction.id, transaction)
        Right(transaction.id)
    }
  }

  override def deleteTransaction(id: TransactionId): Option[TransactionId] = {
    transactions.get(id) match {
      case Some(_) =>
        transactions = transactions - id
        Some(id)
      case None => None
    }
  }
}
