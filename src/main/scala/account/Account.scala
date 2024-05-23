package account

import core.Amount

import java.util.UUID

case class Account(id: AccountId, balance: Amount, version: Int = 0)

case class AccountId(value: UUID) extends AnyVal
