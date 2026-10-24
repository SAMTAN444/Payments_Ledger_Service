package dev.samueltan.ledger.account

import java.util.UUID

data class BalanceResponse(
	val accountId: UUID,
	val currency: String,
	val balance: Long,
)
