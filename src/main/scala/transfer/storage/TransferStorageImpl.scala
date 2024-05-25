package transfer.storage

import transfer.service.TransferId
import transfer.{Internal, Transaction}

import java.util.concurrent.ConcurrentHashMap

class TransferStorageImpl extends TransferStorage {

  private val transfers = new ConcurrentHashMap[TransferId, Transaction]()

  override def getTransfer(id: TransferId): Option[Transaction] = {
    Option(transfers.get(id))
  }

  override def createTransfer(transfer: Transaction): Either[TransferWithIdAlreadyExists, TransferId] = {
    getTransfer(transfer.id) match {
      case Some(existingTransfer) => Left(TransferWithIdAlreadyExists(existingTransfer))
      case None =>
        transfers.put(transfer.id, transfer)
        Right(transfer.id)
    }
  }
}
