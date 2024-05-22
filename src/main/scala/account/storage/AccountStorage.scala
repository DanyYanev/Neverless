package transfer.storage

import account.AccountId
import core.Amount
import transfer.AccountNotFound

sealed trait UpdateError
case object ConcurrentModificationError extends UpdateError

case class Account(id: AccountId, balance: Amount, version: Int = 0)

trait AccountStorage {
  def getAccount(accountId: AccountId): Option[Account]
  def conditionalPutAccount(account: Account): Either[UpdateError, Unit]
  def addBalance(accountId: AccountId, amount: Amount): Either[AccountNotFound, Amount]
}
