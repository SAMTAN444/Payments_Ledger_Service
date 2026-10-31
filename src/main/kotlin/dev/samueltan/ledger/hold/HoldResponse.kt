package dev.samueltan.ledger.hold

import java.time.Instant
import java.util.UUID

data class HoldResponse(
	val id: UUID,
	val fromAccountId: UUID,
	val toAccountId: UUID,
	val amount: Long,
	val currency: String,
	val status: String,
	val createdAt: Instant,
)
