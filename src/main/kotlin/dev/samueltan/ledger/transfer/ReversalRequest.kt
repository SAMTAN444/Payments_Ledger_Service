package dev.samueltan.ledger.transfer

import jakarta.validation.constraints.Positive

data class ReversalRequest(
	@field:Positive
	val amount: Long,
)
