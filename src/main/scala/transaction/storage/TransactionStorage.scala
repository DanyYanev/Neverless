package transaction.storage

import account.AccountId
import transaction.TransactionId
import transaction.storage.models.TransactionRecord


sealed trait TransactionStorageError

case class TransactionWithIdAlreadyExists(existingTransaction: TransactionRecord) extends TransactionStorageError

// I lean towards separate storage solutions as schemas can evolve separately and
// performance probably wont be a consideration.
// Can be an issue if pagination is required, as merging results wouldn't be fun
trait TransactionStorage {
  def getTransaction(id: TransactionId): Option[TransactionRecord]

  def getTransactions(id: AccountId): List[TransactionRecord]

  def createTransaction(transaction: TransactionRecord): Either[TransactionWithIdAlreadyExists, TransactionId]

  def deleteTransaction(id: TransactionId): Option[TransactionId]
}
