package transfer

import org.scalamock.scalatest.MockFactory
import account.AccountId
import account.storage.AccountStorageStub
import core.Amount
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import transfer.storage.{Account, AccountStorage, ConcurrentModificationError, Transfer, TransferStorage, TransferStorageStub}
import withdrawal.scala.WithdrawalService

import java.util.UUID

class TransferServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {
  "TransferServiceImpl" when {
    "requestTransfer is called" should {
      "successfully transfer amounts between accounts" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transferStorage = new TransferStorageStub(Map.empty)

        val transferService = new TransferServiceImpl(null, accountStorage, transferStorage)


        val transfer = Transfer(newTransferId, from, to, Amount(50))
        val result = transferService.requestTransfer(transfer)

        result mustBe Right(transfer.id)
        accountStorage.getAccount(from).get.balance mustBe Amount(50)
        accountStorage.getAccount(to).get.balance mustBe Amount(150)
        transferStorage.getTransfer(transfer.id) mustBe Some(transfer)
      }
      "fail if the from account does not exist" in {
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          to -> Account(to, Amount(100))
        ))

        val transferStorage = new TransferStorageStub(Map.empty)
        val transferService = new TransferServiceImpl(null, accountStorage, transferStorage)

        val from = newAccountId
        val transfer = Transfer(newTransferId, from, to, Amount(50))
        val result = transferService.requestTransfer(transfer)

        result mustBe Left(AccountNotFound(from))
        accountStorage.getAccount(to).get.balance mustBe Amount(100)
        transferStorage.getTransfer(transfer.id) mustBe None
      }
      "fail if the to account does not exist" in {
        val from = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100))
        ))

        val transferStorage = new TransferStorageStub(Map.empty)
        val transferService = new TransferServiceImpl(null, accountStorage, transferStorage)

        val to = newAccountId
        val transfer = Transfer(newTransferId, from, to, Amount(50))
        val result = transferService.requestTransfer(transfer)

        result mustBe Left(AccountNotFound(to))
        accountStorage.getAccount(from).get.balance mustBe Amount(100)
        transferStorage.getTransfer(transfer.id) mustBe None
      }
      "fail if the from account has insufficient funds" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transferStorage = new TransferStorageStub(Map.empty)
        val transferService = new TransferServiceImpl(null, accountStorage, transferStorage)

        val transfer = Transfer(newTransferId, from, to, Amount(150))
        val result = transferService.requestTransfer(transfer)

        result mustBe Left(InsufficientFunds)
        accountStorage.getAccount(from).get.balance mustBe Amount(100)
        accountStorage.getAccount(to).get.balance mustBe Amount(100)
        transferStorage.getTransfer(transfer.id) mustBe None
      }
      "succeed if transfer already exists without changing the balances" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transfer = Transfer(newTransferId, from, to, Amount(50))
        val transferStorage = new TransferStorageStub(Map(
          transfer.id -> transfer
        ))

        val transferService = new TransferServiceImpl(null, accountStorage, transferStorage)

        val result = transferService.requestTransfer(transfer)

        result mustBe Right(transfer.id)
        accountStorage.getAccount(from).get.balance mustBe Amount(100)
        accountStorage.getAccount(to).get.balance mustBe Amount(100)
        transferStorage.getTransfer(transfer.id) mustBe Some(transfer)
      }
      "fail if transfer already exists but has wrong parameters" in {
        val from = newAccountId
        val to = newAccountId
        val accountStorage = new AccountStorageStub(Map(
          from -> Account(from, Amount(100)),
          to -> Account(to, Amount(100))
        ))

        val transfer = Transfer(newTransferId, from, to, Amount(50))
        val transferStorage = new TransferStorageStub(Map(
          transfer.id -> transfer
        ))

        val transferService = new TransferServiceImpl(null, accountStorage, transferStorage)

        val sameTransferDifferentAmount = transfer.copy(amount = Amount(100))
        val result = transferService.requestTransfer(sameTransferDifferentAmount)

        result mustBe Left(IdempotencyViolation)
        accountStorage.getAccount(from).get.balance mustBe Amount(100)
        accountStorage.getAccount(to).get.balance mustBe Amount(100)
        transferStorage.getTransfer(transfer.id).get.amount mustBe Amount(50)
      }
      "fail if from account has been modified concurrently" in {
        val accountStorage = mock[AccountStorage]
        val transferStorage = new TransferStorageStub()
        val transferService = new TransferServiceImpl(mock[WithdrawalService], accountStorage, transferStorage)

        val from = Account(newAccountId, Amount(100), 0)
        val to = Account(newAccountId, Amount(100), 0)
        val transfer = Transfer(newTransferId, from.id, to.id, Amount(50))

        (accountStorage.getAccount _).expects(from.id).returning(Some(from)).once()
        (accountStorage.getAccount _).expects(to.id).returning(Some(to)).once()
        (accountStorage.conditionalPutAccount _)
          .expects(from.copy(balance = from.balance - transfer.amount))
          .returning(Left(ConcurrentModificationError))
          .once()

        val result = transferService.requestTransfer(transfer)

        result mustBe Left(ConcurrentModification)
        transferStorage.getTransfer(transfer.id) mustBe None
      }
    }
  }

  def newAccountId: AccountId = AccountId(UUID.randomUUID())

  def newTransferId: TransferId = TransferId(UUID.randomUUID())
}