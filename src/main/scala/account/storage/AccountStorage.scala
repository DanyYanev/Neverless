package account.storage

import account.{Account, AccountId}
import core.Amount


sealed trait AccountStorageError

case class ConcurrentModification(id: AccountId) extends AccountStorageError

case class AccountNotFound(id: AccountId) extends AccountStorageError


trait AccountStorage {
  //Its more common for this method to return Option[Account], but everywhere we use it, we wrap it anyway.
  def getAccount(accountId: AccountId): Either[AccountNotFound, Account]

  //Note: We can get away with a method that just tries to reserve balance without optimistic locking.
  //      This is interface implements optimistic locking and will probably be needed in production code.
  // Optimistic locking implementation. Account version is incremented on every update.
  // If an update comes and the version is changed since the last read, the update is rejected with ConcurrentModification error.
  def conditionalPutAccount(account: Account): Either[ConcurrentModification, Unit]

  //Crediting an account is an operation that cannot fail, due to other writes.
  def addBalance(accountId: AccountId, amount: Amount): Either[AccountNotFound, Amount]
}
