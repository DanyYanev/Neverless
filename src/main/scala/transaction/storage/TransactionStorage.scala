package transaction.storage

import transaction.service.TransactionId
import transaction.Transaction

//This abstraction is technically not needed, but in production its very common to have more than one error
sealed trait TransactionStorageError

case class TransactionWithIdAlreadyExists(existingTransaction: Transaction) extends TransactionStorageError

// I lean towards separate storage solutions as schemas can evolve separately and
// performance probably wont be a consideration.
// Can be an issue if pagination is required, as merging results wouldn't be fun
trait TransactionStorage {
  def getTransaction(id: TransactionId): Option[Transaction]

  def createTransaction(transaction: Transaction): Either[TransactionStorageError, TransactionId]
}
