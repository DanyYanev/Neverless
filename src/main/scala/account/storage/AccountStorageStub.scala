package account.storage

import account.AccountId
import core.Amount
import transfer.AccountNotFound
import transfer.storage.{Account, AccountStorage, ConcurrentModificationError, UpdateError}

class AccountStorageStub(var accounts: Map[AccountId, Account] = Map.empty) extends AccountStorage {

  override def addBalance(accountId: AccountId, amount: Amount): Either[AccountNotFound, Amount] = {
    accounts.get(accountId) match {
      case Some(account) =>
        accounts = accounts.updated(accountId, account.copy(balance = account.balance + amount))
        Right(accounts(accountId).balance)
      case None =>
        Left(AccountNotFound(accountId))
    }
  }

  override def getAccount(id: AccountId): Option[Account] = {
    accounts.get(id)
  }

  override def conditionalPutAccount(account: Account): Either[UpdateError, Unit] = {
    accounts.get(account.id) match {
      case Some(existingAccount) if existingAccount.version != account.version =>
        Left(ConcurrentModificationError)
      case _ =>
        accounts = accounts.updated(account.id, account.copy(version = account.version + 1))
        Right(())
    }
  }
}
