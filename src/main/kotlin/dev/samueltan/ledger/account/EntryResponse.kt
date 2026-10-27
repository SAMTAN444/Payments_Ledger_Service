package dev.samueltan.ledger.account

import java.time.Instant
import java.util.UUID

data class EntryResponse(
	val entryId: Long,
	val transferId: UUID,
	val amount: Long,
	val counterpartyAccountId: UUID,
	val createdAt: Instant,
)
