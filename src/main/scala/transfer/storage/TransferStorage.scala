package transfer.storage

import core.{AccountId, Address, Amount}
import transfer.TransferId

case class Transfer(id: TransferId, from: AccountId, to: AccountId, amount: Amount)
case class Withdrawal(id: TransferId, from: AccountId, to: Address, amount: Amount)

sealed trait TransferStorageError
case object TransferIdAlreadyExists extends TransferStorageError


trait TransferStorage {
  def getTransfer(id: TransferId): Option[Transfer]
  def createTransfer(transfer: Transfer): Either[TransferStorageError, TransferId]
}
