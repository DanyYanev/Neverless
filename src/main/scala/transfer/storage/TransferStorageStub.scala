package transfer.storage

import transfer.Transaction
import transfer.service.TransferId

class TransferStorageStub(var transfers: Map[TransferId, Transaction] = Map.empty) extends TransferStorage {
  override def getTransfer(id: TransferId): Option[Transaction] = transfers.get(id)

  override def createTransfer(transfer: Transaction): Either[TransferWithIdAlreadyExists, TransferId] = {
    transfers.get(transfer.id) match {
      case Some(existingTransfer) => Left(TransferWithIdAlreadyExists(existingTransfer))
      case None =>
        transfers = transfers.updated(transfer.id, transfer)
        Right(transfer.id)
    }
  }
}
