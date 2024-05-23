package account.storage

import account.{Account, AccountId}
import core.Amount


sealed trait AccountStorageError

case class ConcurrentModification(id: AccountId) extends AccountStorageError

case class AccountNotFound(id: AccountId) extends AccountStorageError

trait AccountStorage {
  //Its more common for this method to return Option[Account], but everywhere we use it, we wrap it anyway.
  def getAccount(accountId: AccountId): Either[AccountNotFound, Account]

  def conditionalPutAccount(account: Account): Either[ConcurrentModification, Unit]

  def addBalance(accountId: AccountId, amount: Amount): Either[AccountNotFound, Amount]
}
