package dev.samueltan.ledger.account

data class EntriesPageResponse(
	val entries: List<EntryResponse>,
	val nextCursor: Long?,
)
