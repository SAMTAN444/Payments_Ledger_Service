package dev.samueltan.ledger.account

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern


data class CreateAccountRequest(
	@field:NotBlank
	val ownerName: String,

	@field:Pattern(regexp = "[A-Z]{3}")
	val currency: String,

	@field:Pattern(regexp = "CUSTOMER|SYSTEM")
	val kind: String,

)
