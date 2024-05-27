package transaction.service

import account.storage.{AccountNotFound, AccountStorage, AccountStorageStub, ConcurrentModification}
import account.{Account, AccountId}
import core.{Address, Amount}
import org.scalamock.matchers.ArgCapture.CaptureOne
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import transaction.storage.TransactionStorageStub
import transaction._
import withdrawal.scala.{WithdrawalId, WithdrawalService, IdempotencyViolation => WithdrawalIdempotencyViolation, Processing => WithdrawalStatusProcessing}

import java.time.Instant
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

        val transaction = Internal(newTransactionId, from, to, Amount(50), Instant.now())
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
        val transaction = Internal(newTransactionId, from, to, Amount(50), Instant.now())
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
        val transaction = Internal(newTransactionId, from, to, Amount(50), Instant.now())
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

        val transaction = Internal(newTransactionId, from, to, Amount(150), Instant.now())
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

        val transaction = Internal(newTransactionId, from, to, Amount(50), Instant.now())
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

        val transaction = Internal(newTransactionId, from, to, Amount(50), Instant.now())
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
        val transaction = Internal(newTransactionId, from.id, to.id, Amount(50), Instant.now())

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
    "requestWithdrawal is called" should {
      "successfully withdraw amounts from an account" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage)

        val withdrawal = WithdrawalRequest(newTransactionId, from.id, Address("to"), Amount(50), Instant.now())
        val capturedWithdrawalId = CaptureOne[WithdrawalId]()

        (withdrawalService.requestWithdrawal _)
          .expects(
            capture(capturedWithdrawalId),
            Address("to"),
            Amount(50)
          )
          .returning(Right(WithdrawalId(UUID.randomUUID())))
          .once()

        val result = transactionService.requestWithdrawal(withdrawal)

        result mustBe Right(withdrawal.id)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(50))
        val transaction = transactionStorage.getTransaction(withdrawal.id).get
        val expectedWithdrawalId = capturedWithdrawalId.value
        transaction must matchPattern {
          case Withdrawal(withdrawal.id, `expectedWithdrawalId`, withdrawal.from, withdrawal.to, Amount(50), withdrawal.timestamp) =>
        }
      }
      "fail if the account does not exist" in {
        val accountStorage = new AccountStorageStub(Map.empty)
        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(mock[WithdrawalService], accountStorage, transactionStorage)

        val from = newAccountId
        val withdrawal = WithdrawalRequest(newTransactionId, from, Address("to"), Amount(50), Instant.now())

        val result = transactionService.requestWithdrawal(withdrawal)

        result mustBe Left(AccountStorageFault(AccountNotFound(from)))
        transactionStorage.getTransaction(withdrawal.id) mustBe None
      }
      "fail if the account has insufficient funds" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage)

        val withdrawal = WithdrawalRequest(newTransactionId, from.id, Address("to"), Amount(150), Instant.now())

        val result = transactionService.requestWithdrawal(withdrawal)

        result mustBe Left(InsufficientFunds)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(withdrawal.id) mustBe None
      }
      "fail if the account has been modified concurrently" in {
        val accountStorage = mock[AccountStorage]
        val transactionStorage = new TransactionStorageStub()
        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage)

        val from = Account(newAccountId, Amount(100), 0)
        val withdrawal = WithdrawalRequest(newTransactionId, from.id, Address("to"), Amount(50), Instant.now())

        (accountStorage.getAccount _).expects(from.id).returning(Right(from)).once()
        (accountStorage.conditionalPutAccount _)
          .expects(from.copy(balance = from.balance - withdrawal.amount))
          .returning(Left(ConcurrentModification(from.id)))
          .once()

        val result = transactionService.requestWithdrawal(withdrawal)

        result mustBe Left(AccountStorageFault(ConcurrentModification(from.id)))
        transactionStorage.getTransaction(withdrawal.id) mustBe None
      }
      "succeed if withdrawal already exists" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val withdrawal = Withdrawal(newTransactionId, newWithdrawalId, from.id, Address("to"), Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          withdrawal.id -> withdrawal
        ))

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage)

        val result = transactionService.requestWithdrawal(WithdrawalRequest(withdrawal.id, withdrawal.from, withdrawal.to, withdrawal.amount, Instant.now()))

        result mustBe Right(withdrawal.id)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(withdrawal.id) mustBe Some(withdrawal)
      }
      "fail if withdrawal already exists but has wrong parameters" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val withdrawal = Withdrawal(newTransactionId, newWithdrawalId, from.id, Address("to"), Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          withdrawal.id -> withdrawal
        ))

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage)

        val result = transactionService.requestWithdrawal(WithdrawalRequest(withdrawal.id, withdrawal.from, withdrawal.to, Amount(100), Instant.now()))

        result mustBe Left(IdempotencyViolation)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(withdrawal.id).get.amount mustBe Amount(50)
      }
      "fail if withdrawal service fails" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val withdrawal = WithdrawalRequest(newTransactionId, from.id, Address("to"), Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map.empty)

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage)

        (withdrawalService.requestWithdrawal _)
          .expects(*, *, *)
          .returning(Left(WithdrawalIdempotencyViolation()))
          .once()

        val result = transactionService.requestWithdrawal(withdrawal)

        result mustBe Left(IdempotencyViolation)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(withdrawal.id) mustBe None
      }
    }
    "getTransactionStatus is called" should {
      "always return completed for internal transfer" in {
        val transaction = Internal(newTransactionId, newAccountId, newAccountId, Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          transaction.id -> transaction
        ))

        val transactionService = new TransactionServiceImpl(null, null, transactionStorage)

        val result = transactionService.getTransactionStatus(transaction.id)

        result mustBe Some(Completed)
      }
      "return the status of a withdrawal" in {
        val withdrawal = Withdrawal(newTransactionId, newWithdrawalId, newAccountId, Address("to"), Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          withdrawal.id -> withdrawal
        ))

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, null, transactionStorage)

        (withdrawalService.getWithdrawalStatus _)
          .expects(withdrawal.withdrawalId)
          .returning(Some(WithdrawalStatusProcessing))
          .once()

        val result = transactionService.getTransactionStatus(withdrawal.id)

        result mustBe Some(Processing)
      }
      "return None if the transaction does not exist" in {
        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, null, transactionStorage)

        val result = transactionService.getTransactionStatus(newTransactionId)

        result mustBe None
      }
    }
  }

  def newAccountId: AccountId = AccountId(UUID.randomUUID())

  def newTransactionId: TransactionId = TransactionId(UUID.randomUUID())

  def newWithdrawalId: WithdrawalId = WithdrawalId(UUID.randomUUID())
}