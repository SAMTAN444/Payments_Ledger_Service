package dev.samueltan.ledger.transfer

import java.time.Instant
import java.util.UUID

data class TransferResponse(
	val id: UUID,
	val fromAccountId: UUID,
	val toAccountId: UUID,
	val amount: Long,
	val currency: String,
	val createdAt: Instant,
	val reversesTransferId: UUID?,
)
