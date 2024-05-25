package transfer.storage

import transfer.service.TransferId
import transfer.Transaction

//This abstraction is technically not needed, but in production its very common to have more than one error
sealed trait TransferStorageError

case class TransferWithIdAlreadyExists(existingTransfer: Transaction) extends TransferStorageError

// I lean towards separate storage solutions as schemas can evolve separately and
// performance probably wont be a consideration.
// Can be an issue if pagination is required, as merging results wouldn't be fun
trait TransferStorage {
  def getTransfer(id: TransferId): Option[Transaction]

  def createTransfer(transfer: Transaction): Either[TransferStorageError, TransferId]
}
