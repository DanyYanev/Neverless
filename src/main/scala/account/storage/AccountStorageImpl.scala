package transfer.storage
import core.AccountId

import java.util.concurrent.ConcurrentHashMap

class AccountStorageImpl extends AccountStorage {
  private val accounts = new ConcurrentHashMap[AccountId, Account]()

  override def getAccount(accountId: AccountId): Option[Account] = Option(accounts.get(accountId))

  override def conditionalPutAccount(account: Account): Either[UpdateError, Unit] = {
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

    if(newValue == expectedValue) {
      Right(())
    } else {
      Left(ConcurrentModificationError)
    }
  }
}
