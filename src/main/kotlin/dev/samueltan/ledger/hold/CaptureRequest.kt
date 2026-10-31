package dev.samueltan.ledger.hold

import jakarta.validation.constraints.Positive

data class CaptureRequest(
	@field:Positive
	val amount: Long,
)
