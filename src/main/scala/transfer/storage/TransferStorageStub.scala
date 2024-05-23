package transfer.storage

import transfer.{Transfer, TransferId}

class TransferStorageStub(var transfers: Map[TransferId, Transfer] = Map.empty) extends TransferStorage {
  override def getTransfer(id: TransferId): Option[Transfer] = transfers.get(id)

  override def createTransfer(transfer: Transfer): Either[TransferWithIdAlreadyExists, TransferId] = {
    transfers.get(transfer.id) match {
      case Some(existingTransfer) => Left(TransferWithIdAlreadyExists(existingTransfer))
      case None =>
        transfers = transfers.updated(transfer.id, transfer)
        Right(transfer.id)
    }
  }
}
