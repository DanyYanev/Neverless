package transfer.storage

import account.AccountId
import core.{Address, Amount}
import transfer.{Transfer, TransferId}

//This abstraction is technically not needed, but in production its very common to have more than one error
sealed trait TransferStorageError

case class TransferWithIdAlreadyExists(existingTransfer: Transfer) extends TransferStorageError

trait TransferStorage {
  def getTransfer(id: TransferId): Option[Transfer]

  def createTransfer(transfer: Transfer): Either[TransferStorageError, TransferId]
}
