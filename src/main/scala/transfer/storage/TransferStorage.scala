package transfer.storage

import account.AccountId
import core.{Address, Amount}
import transfer.TransferId

case class Transfer(id: TransferId, from: AccountId, to: AccountId, amount: Amount)

case class Withdrawal(id: TransferId, from: AccountId, to: Address, amount: Amount)

sealed trait TransferStorageError

case class TransferWithIdAlreadyExists(transfer: Transfer) extends TransferStorageError

trait TransferStorage {
  def getTransfer(id: TransferId): Option[Transfer]

  def createTransfer(transfer: Transfer): Either[TransferStorageError, TransferId]
}
