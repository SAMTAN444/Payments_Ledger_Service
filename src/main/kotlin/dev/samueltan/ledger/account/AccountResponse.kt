package dev.samueltan.ledger.account

import java.time.Instant
import java.util.UUID

data class AccountResponse(
	val id: UUID,
	val ownerName: String,
	val currency: String,
	val kind: String,
	val createdAt: Instant,
)
