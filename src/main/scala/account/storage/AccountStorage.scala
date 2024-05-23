package transfer.storage

import account.AccountId
import core.Amount
import transfer.AccountNotFound

case class Account(id: AccountId, balance: Amount, version: Int = 0)

sealed trait UpdateError

case object ConcurrentModificationError extends UpdateError

trait AccountStorage {
  def getAccount(accountId: AccountId): Option[Account]

  def conditionalPutAccount(account: Account): Either[UpdateError, Unit]

  def addBalance(accountId: AccountId, amount: Amount): Either[AccountNotFound, Amount]
}
