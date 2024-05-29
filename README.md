Intro

I've implemented the service in Scala (2.13.14) with sbt (1.10.0) & Java SDK 20

Used standard Scala functional libraries like http4s, cats, circe etc.

Postman collection with example requests is available in /docs/

Some notes:

1. Most design decisions were future proofed, although probably there was no need for this project
2. There is a serious issue arising from not having something like transactions for my in-memory stores.
   Explained in detail in `TransactionServiceImpl.scala`
3. Not all tests scenarios were implemented in the contract tests.
   The contract tests came out a bit robust, there are better ways to write them.
   Tapir would've helped with that, but I didn't want to add more dependencies and rework current controller.
   Tapir would've also provided swagger files. Routes available in `*Controller`.
4. AccountStorage uses defaults as no endpoints for managing accounts are provided.
   See `account/storage/DefaultValues`
5. The insides of the services aren't using IO, which in practice would be the case. Code wouldn't be much different.
6. Amount class is Int for ease of use
7. API Errors could be more descriptive, but left them as statuses for simplicity

If you have any questions or need more information, feel free to drop me an email :)

Endpoints:

```
    case req@POST -> Root / "accounts" / UUIDVar(accountId) / "transactions" / "internal" =>
      req.decode[InternalTransactionRequest](requestInternalTransaction(accountId, _))

    case req@POST -> Root / "accounts" / UUIDVar(accountId) / "transactions" / "withdrawal" =>
      req.decode[WithdrawalRequest](requestWithdrawal(accountId, _))

    case GET -> Root / "accounts" / UUIDVar(accountId) / "transactions" =>
      getAllTransactions(accountId)

    case GET -> Root / "accounts" / UUIDVar(accountId) / "transactions" / UUIDVar(transactionId) =>
      getTransaction(accountId, transactionId)
```

8. The endpoint that returns transactions for account should return all transaction the account is involved in, not just
   the ones initiated by the account.
9. Transaction should be able to be fetched by ID by either initiator or recipients accountID.
   Right now only initiator can fetch that transaction.
