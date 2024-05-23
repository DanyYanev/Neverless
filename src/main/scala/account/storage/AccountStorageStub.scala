package account.storage

import account.{Account, AccountId}
import core.Amount

class AccountStorageStub(var accounts: Map[AccountId, Account] = Map.empty) extends AccountStorage {

  override def getAccount(accountId: AccountId): Either[AccountNotFound, Account] = {
    accounts.get(accountId) match {
      case Some(account) => Right(account)
      case None => Left(AccountNotFound(accountId))
    }
  }

  override def conditionalPutAccount(account: Account): Either[ConcurrentModification, Unit] = {
    accounts.get(account.id) match {
      case Some(existingAccount) if existingAccount.version != account.version =>
        Left(ConcurrentModification(account.id))
      case _ =>
        accounts = accounts.updated(account.id, account.copy(version = account.version + 1))
        Right(())
    }
  }

  override def addBalance(accountId: AccountId, amount: Amount): Either[AccountNotFound, Amount] = {
    accounts.get(accountId) match {
      case Some(account) =>
        accounts = accounts.updated(accountId, account.copy(balance = account.balance + amount))
        Right(accounts(accountId).balance)
      case None =>
        Left(AccountNotFound(accountId))
    }
  }
}
