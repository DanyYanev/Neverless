package transaction

import org.scalamock.scalatest.MockFactory
import account.{Account, AccountId}
import account.storage.{AccountNotFound, AccountStorage, AccountStorageStub, ConcurrentModification}
import core.Amount
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import transaction.service.{AccountStorageFault, IdempotencyViolation, InsufficientFunds, TransactionId, TransactionServiceImpl}
import transaction.storage.TransactionStorageStub
import withdrawal.scala.WithdrawalService

import java.util.UUID

class TransactionServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {
  "TransactionServiceImpl" when {
    "requestTransaction is called" should {
      "successfully transaction amounts between accounts" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage)

        val transaction = Internal(newTransactionId, from, to, Amount(50))
        val result = transactionService.requestTransaction(transaction)

        result mustBe Right(transaction.id)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(50))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(150))
        transactionStorage.getTransaction(transaction.id) mustBe Some(transaction)
      }
      "fail if the from account does not exist" in {
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          to -> Account(to, Amount(100))
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage)

        val from = newAccountId
        val transaction = Internal(newTransactionId, from, to, Amount(50))
        val result = transactionService.requestTransaction(transaction)

        result mustBe Left(AccountStorageFault(AccountNotFound(from)))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(transaction.id) mustBe None
      }
      "fail if the to account does not exist" in {
        val from = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100))
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage)

        val to = newAccountId
        val transaction = Internal(newTransactionId, from, to, Amount(50))
        val result = transactionService.requestTransaction(transaction)

        result mustBe Left(AccountStorageFault(AccountNotFound(to)))
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(transaction.id) mustBe None
      }
      "fail if the from account has insufficient funds" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage)

        val transaction = Internal(newTransactionId, from, to, Amount(150))
        val result = transactionService.requestTransaction(transaction)

        result mustBe Left(InsufficientFunds)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(transaction.id) mustBe None
      }
      "succeed if transaction already exists without changing the balances" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transaction = Internal(newTransactionId, from, to, Amount(50))
        val transactionStorage = new TransactionStorageStub(Map(
          transaction.id -> transaction
        ))

        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage)

        val result = transactionService.requestTransaction(transaction)

        result mustBe Right(transaction.id)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(transaction.id) mustBe Some(transaction)
      }
      "fail if transaction already exists but has wrong parameters" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transaction = Internal(newTransactionId, from, to, Amount(50))
        val transactionStorage = new TransactionStorageStub(Map(
          transaction.id -> transaction
        ))

        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage)

        val sameTransactionDifferentAmount = transaction.copy(amount = Amount(100))
        val result = transactionService.requestTransaction(sameTransactionDifferentAmount)

        result mustBe Left(IdempotencyViolation)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(transaction.id).get.amount mustBe Amount(50)
      }
      "fail if from account has been modified concurrently" in {
        val accountStorage = mock[AccountStorage]
        val transactionStorage = new TransactionStorageStub()
        val transactionService = new TransactionServiceImpl(mock[WithdrawalService], accountStorage, transactionStorage)

        val from = Account(newAccountId, Amount(100), 0)
        val to = Account(newAccountId, Amount(100), 0)
        val transaction = Internal(newTransactionId, from.id, to.id, Amount(50))

        (accountStorage.getAccount _).expects(from.id).returning(Right(from)).once()
        (accountStorage.getAccount _).expects(to.id).returning(Right(to)).once()
        (accountStorage.conditionalPutAccount _)
          .expects(from.copy(balance = from.balance - transaction.amount))
          .returning(Left(ConcurrentModification(from.id)))
          .once()

        val result = transactionService.requestTransaction(transaction)

        result mustBe Left(AccountStorageFault(ConcurrentModification(from.id)))
        transactionStorage.getTransaction(transaction.id) mustBe None
      }
    }
  }

  def newAccountId: AccountId = AccountId(UUID.randomUUID())

  def newTransactionId: TransactionId = TransactionId(UUID.randomUUID())
}