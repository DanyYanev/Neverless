package transfer.storage

import transfer.{Transfer, TransferId}

import java.util.concurrent.ConcurrentHashMap

class TransferStorageImpl extends TransferStorage {

  private val transfers = new ConcurrentHashMap[TransferId, Transfer]()

  override def getTransfer(id: TransferId): Option[Transfer] = {
    Option(transfers.get(id))
  }

  override def createTransfer(transfer: Transfer): Either[TransferWithIdAlreadyExists, TransferId] = {
    getTransfer(transfer.id) match {
      case Some(existingTransfer) => Left(TransferWithIdAlreadyExists(existingTransfer))
      case None =>
        transfers.put(transfer.id, transfer)
        Right(transfer.id)
    }
  }
}
