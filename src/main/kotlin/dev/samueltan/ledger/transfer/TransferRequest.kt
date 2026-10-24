package dev.samueltan.ledger.transfer

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.util.UUID

data class TransferRequest(
	val fromAccountId: UUID,
	val toAccountId: UUID,

	@field:Positive
	val amount: Long,

	@field:Pattern(regexp = "[A-Z]{3}")
	val currency: String,
) {
	@get:AssertTrue(message = "fromAccountId and toAccountId must be different")
	val accountsAreDifferent: Boolean
		get() = fromAccountId != toAccountId
}
