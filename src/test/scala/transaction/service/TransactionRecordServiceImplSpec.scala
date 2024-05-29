package transaction.service

import account.storage.{AccountNotFound, AccountStorage, AccountStorageStub, ConcurrentModification}
import account.{Account, AccountId}
import core.{Address, Amount}
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import transaction._
import transaction.service.models.Transaction
import transaction.storage.TransactionStorageStub
import transaction.storage.models.{InternalRecord, WithdrawalRecord}
import utils.{UUIDGenerator, UUIDGeneratorImpl}
import withdrawal.scala.{WithdrawalId, WithdrawalService, IdempotencyViolation => WithdrawalIdempotencyViolation, Processing => WithdrawalStatusProcessing}

import java.time.Instant
import java.util.UUID

class TransactionRecordServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {
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
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val record = InternalRecord(newTransactionId, from, to, Amount(50), Instant.now())
        val result = transactionService.requestTransaction(record.id, record.from, record.to, record.amount, record.timestamp)

        result mustBe Right(record.id)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(50))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(150))
        transactionStorage.getTransaction(record.id) mustBe Some(record)
      }
      "fail if the from account does not exist" in {
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          to -> Account(to, Amount(100))
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val from = newAccountId
        val record = InternalRecord(newTransactionId, from, to, Amount(50), Instant.now())
        val result = transactionService.requestTransaction(record.id, record.from, record.to, record.amount, record.timestamp)

        result mustBe Left(AccountStorageFault(AccountNotFound(from)))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(record.id) mustBe None
      }
      "fail if the to account does not exist" in {
        val from = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100))
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val to = newAccountId
        val record = InternalRecord(newTransactionId, from, to, Amount(50), Instant.now())
        val result = transactionService.requestTransaction(record.id, record.from, record.to, record.amount, record.timestamp)

        result mustBe Left(AccountStorageFault(AccountNotFound(to)))
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(record.id) mustBe None
      }
      "fail if the from account has insufficient funds" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val record = InternalRecord(newTransactionId, from, to, Amount(150), Instant.now())
        val result = transactionService.requestTransaction(record.id, record.from, record.to, record.amount, record.timestamp)

        result mustBe Left(InsufficientFunds)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(record.id) mustBe None
      }
      "succeed if transaction already exists without changing the balances" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val record = InternalRecord(newTransactionId, from, to, Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          record.id -> record
        ))

        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val result = transactionService.requestTransaction(record.id, record.from, record.to, record.amount, record.timestamp)

        result mustBe Right(record.id)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(record.id) mustBe Some(record)
      }
      "fail if transaction already exists but has wrong parameters" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val record = InternalRecord(newTransactionId, from, to, Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          record.id -> record
        ))

        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val sameRecordDifferentAmount = record.copy(amount = Amount(100))
        val result = transactionService.requestTransaction(
          sameRecordDifferentAmount.id,
          sameRecordDifferentAmount.from,
          sameRecordDifferentAmount.to,
          sameRecordDifferentAmount.amount,
          sameRecordDifferentAmount.timestamp
        )

        result mustBe Left(IdempotencyViolation)
        accountStorage.getAccount(from).map(_.balance) mustBe Right(Amount(100))
        accountStorage.getAccount(to).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(record.id).get.amount mustBe Amount(50)
      }
      "fail if from account has been modified concurrently" in {
        val accountStorage = mock[AccountStorage]
        val transactionStorage = new TransactionStorageStub()
        val transactionService = new TransactionServiceImpl(mock[WithdrawalService], accountStorage, transactionStorage, UUIDGeneratorImpl)

        val from = Account(newAccountId, Amount(100))
        val to = Account(newAccountId, Amount(100))
        val record = InternalRecord(newTransactionId, from.id, to.id, Amount(50), Instant.now())

        (accountStorage.getAccount _).expects(from.id).returning(Right(from)).once()
        (accountStorage.getAccount _).expects(to.id).returning(Right(to)).once()
        (accountStorage.conditionalPutAccount _)
          .expects(from.copy(balance = from.balance - record.amount))
          .returning(Left(ConcurrentModification(from.id)))
          .once()

        val result = transactionService.requestTransaction(record.id, record.from, record.to, record.amount, record.timestamp)

        result mustBe Left(AccountStorageFault(ConcurrentModification(from.id)))
        transactionStorage.getTransaction(record.id) mustBe None
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
        val generator = mock[UUIDGenerator]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage, generator)

        val transactionId = newTransactionId
        val withdrawalId = newWithdrawalId
        val to = Address("to")
        val amount = Amount(50)
        val timestamp = Instant.now()

        (generator.generateUUID _)
          .expects()
          .returning(withdrawalId.value)
          .once()

        (withdrawalService.requestWithdrawal _)
          .expects(withdrawalId, to, amount)
          .returning(Right(withdrawalId))
          .once()

        val result = transactionService.requestWithdrawal(transactionId, from.id, to, amount, timestamp)

        result mustBe Right(transactionId)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(50))

        val transaction = transactionStorage.getTransaction(transactionId).get
        transaction must matchPattern {
          case WithdrawalRecord(`transactionId`, `withdrawalId`, from.id, `to`, `amount`, `timestamp`) =>
        }
      }
      "fail if the account does not exist" in {
        val accountStorage = new AccountStorageStub(Map.empty)
        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(mock[WithdrawalService], accountStorage, transactionStorage, UUIDGeneratorImpl)

        val id = newTransactionId
        val from = newAccountId
        val address = Address("to")
        val amount = Amount(50)
        val timestamp = Instant.now()

        val result = transactionService.requestWithdrawal(id, from, address, amount, timestamp)

        result mustBe Left(AccountStorageFault(AccountNotFound(from)))
        transactionStorage.getTransaction(id) mustBe None
      }
      "fail if the account has insufficient funds" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val transactionStorage = new TransactionStorageStub(Map.empty)
        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val id = newTransactionId
        val result = transactionService.requestWithdrawal(id, from.id, Address("to"), Amount(150), Instant.now())

        result mustBe Left(InsufficientFunds)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(id) mustBe None
      }
      "fail if the account has been modified concurrently" in {
        val accountStorage = mock[AccountStorage]
        val transactionStorage = new TransactionStorageStub()
        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val id = newTransactionId
        val from = Account(newAccountId, Amount(100))
        val amount = Amount(50)


        (accountStorage.getAccount _).expects(from.id).returning(Right(from)).once()
        (accountStorage.conditionalPutAccount _)
          .expects(from.copy(balance = from.balance - amount))
          .returning(Left(ConcurrentModification(from.id)))
          .once()

        val result = transactionService.requestWithdrawal(id, from.id, Address("to"), amount, Instant.now())

        result mustBe Left(AccountStorageFault(ConcurrentModification(from.id)))
        transactionStorage.getTransaction(id) mustBe None
      }
      "succeed if withdrawal already exists" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val withdrawal = WithdrawalRecord(newTransactionId, newWithdrawalId, from.id, Address("to"), Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          withdrawal.id -> withdrawal
        ))

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val result = transactionService.requestWithdrawal(withdrawal.id, withdrawal.from, withdrawal.to, withdrawal.amount, Instant.now())

        result mustBe Right(withdrawal.id)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(withdrawal.id) mustBe Some(withdrawal)
      }
      "fail if withdrawal already exists but has wrong parameters" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val withdrawal = WithdrawalRecord(newTransactionId, newWithdrawalId, from.id, Address("to"), Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          withdrawal.id -> withdrawal
        ))

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val result = transactionService.requestWithdrawal(withdrawal.id, withdrawal.from, withdrawal.to, Amount(100), Instant.now())

        result mustBe Left(IdempotencyViolation)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(withdrawal.id).get.amount mustBe Amount(50)
      }
      "fail if withdrawal service fails" in {
        val from = Account(newAccountId, Amount(100))
        val accountStorage = new AccountStorageStub(Map(
          from.id -> from,
        ))

        val id = newTransactionId
        val transactionStorage = new TransactionStorageStub(Map.empty)

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage, UUIDGeneratorImpl)

        (withdrawalService.requestWithdrawal _)
          .expects(*, *, *)
          .returning(Left(WithdrawalIdempotencyViolation()))
          .once()

        val result = transactionService.requestWithdrawal(id, from.id, Address("to"), Amount(50), Instant.now())

        result mustBe Left(IdempotencyViolation)
        accountStorage.getAccount(from.id).map(_.balance) mustBe Right(Amount(100))
        transactionStorage.getTransaction(id) mustBe None
      }
    }
    "getTransaction is called" should {
      "return the transaction if it exists" in {
        val record = InternalRecord(newTransactionId, newAccountId, newAccountId, Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          record.id -> record
        ))

        val transactionService = new TransactionServiceImpl(null, null, transactionStorage, UUIDGeneratorImpl)

        val result = transactionService.getTransaction(record.from, record.id)

        result mustBe Some(Transaction.from(record, TransactionStatus.Completed))
      }
      "return None if the transaction doesn't exist" in {
        val transactionStorage = new TransactionStorageStub(Map.empty)
        val transactionService = new TransactionServiceImpl(null, null, transactionStorage, UUIDGeneratorImpl)

        val result = transactionService.getTransaction(newAccountId, newTransactionId)

        result mustBe None
      }
      "return None if the transaction doesn't belong to the account" in {
        val record = InternalRecord(newTransactionId, newAccountId, newAccountId, Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          record.id -> record
        ))

        val transactionService = new TransactionServiceImpl(null, null, transactionStorage, UUIDGeneratorImpl)

        val result = transactionService.getTransaction(newAccountId, newTransactionId)

        result mustBe None
      }
      "return transaction with status from withdrawal service" in {
        val withdrawal = WithdrawalRecord(newTransactionId, newWithdrawalId, newAccountId, Address("to"), Amount(50), Instant.now())
        val transactionStorage = new TransactionStorageStub(Map(
          withdrawal.id -> withdrawal
        ))

        val withdrawalService = mock[WithdrawalService]
        val transactionService = new TransactionServiceImpl(withdrawalService, null, transactionStorage, UUIDGeneratorImpl)

        (withdrawalService.getWithdrawalStatus _)
          .expects(withdrawal.withdrawalId)
          .returning(Some(WithdrawalStatusProcessing))
          .once()

        val result = transactionService.getTransaction(withdrawal.from, withdrawal.id)

        result mustBe Some(Transaction.from(withdrawal, TransactionStatus.Processing))
      }
    }
    "getTransactionHistory for account" should {
      "return all transactions for the account" in {
        val accountId = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          accountId -> Account(accountId, Amount(100))
        ))
        val transactions = List(
          InternalRecord(newTransactionId, accountId, newAccountId, Amount(50), Instant.now()),
          InternalRecord(newTransactionId, accountId, newAccountId, Amount(60), Instant.now())
        )

        val allTransactions = InternalRecord(newTransactionId, newAccountId, newAccountId, Amount(70), Instant.now()) :: transactions
        val transactionStorage = new TransactionStorageStub(allTransactions.map(t => t.id -> t).toMap)
        val transactionService = new TransactionServiceImpl(null, accountStorage, transactionStorage, UUIDGeneratorImpl)

        val result = transactionService.getTransactionHistory(accountId)

        result mustBe Right(transactions.map(Transaction.from(_, TransactionStatus.Completed)))
      }

    }
  }

  def newAccountId: AccountId = AccountId(UUID.randomUUID())

  def newTransactionId: TransactionId = TransactionId(UUID.randomUUID())

  def newWithdrawalId: WithdrawalId = WithdrawalId(UUID.randomUUID())
}