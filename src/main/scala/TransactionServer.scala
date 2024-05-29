import account.storage.{AccountStorageImpl, DefaultValues}
import cats.effect.{IO, IOApp}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import transaction.controller.TransactionController
import transaction.service.TransactionServiceImpl
import transaction.storage.TransactionStorageImpl
import utils.UUIDGeneratorImpl
import withdrawal.java.WithdrawalServiceStub
import withdrawal.scala.WithdrawalServiceImpl

object TransactionServer extends IOApp.Simple {
  override def run: IO[Unit] = {
    val accountStorage = new AccountStorageImpl(DefaultValues.accounts)
    val transactionStorage = new TransactionStorageImpl()
    val withdrawalService = new WithdrawalServiceImpl(new WithdrawalServiceStub())

    val transactionService = new TransactionServiceImpl(withdrawalService, accountStorage, transactionStorage, UUIDGeneratorImpl)

    val transactionController = new TransactionController(transactionService, java.time.Clock.systemUTC())

    val httpApp = Router("/api" -> transactionController.routes).orNotFound

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
  }
}
