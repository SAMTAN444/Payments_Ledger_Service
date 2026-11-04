package dev.samueltan.ledger.admin

data class ReconciliationResponse(
	val ok: Boolean,
	val problems: List<String>,
)
