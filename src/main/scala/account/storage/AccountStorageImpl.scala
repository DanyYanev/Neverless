package account.storage

import account.{Account, AccountId}
import core.Amount

import java.util.concurrent.ConcurrentHashMap

class AccountStorageImpl extends AccountStorage {
  private val accounts = new ConcurrentHashMap[AccountId, Account]()

  override def getAccount(accountId: AccountId): Either[AccountNotFound, Account] =
    Option(accounts.get(accountId)) match {
      case Some(account) =>
        Right(account)
      case None =>
        Left(AccountNotFound(accountId))
    }

  override def conditionalPutAccount(account: Account): Either[ConcurrentModification, Unit] = {
    val newValue = accounts.compute(account.id, (_, currentAccount) => {
      Option(currentAccount) match {
        case None =>
          account
        case Some(currentAccount) if currentAccount.version == account.version =>
          account.copy(version = account.version + 1)
        case Some(currentAccount) =>
          currentAccount
      }
    })

    val expectedValue = account.copy(version = account.version + 1)

    if (newValue == expectedValue) {
      Right(())
    } else {
      Left(ConcurrentModification(account.id))
    }
  }

  override def addBalance(accountId: AccountId, amount: Amount): Either[AccountNotFound, Amount] =
    try {
      val updatedAccount = accounts.compute(accountId, (_, currentAccount) => {
        Option(currentAccount) match {
          case Some(currentAccount) =>
            currentAccount.copy(balance = currentAccount.balance + amount)
          case None =>
            throw new NoSuchElementException
        }
      })
      Right(updatedAccount.balance)
    } catch {
      case _: NoSuchElementException =>
        Left(AccountNotFound(accountId))
    }
}
