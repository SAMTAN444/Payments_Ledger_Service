package dev.samueltan.ledger.transfer

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/transfers")
class TransferController(
	private val transferService: TransferService,
) {
	@PostMapping
	fun create(
		@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) idempotencyKey: String,
		@Valid @RequestBody request: TransferRequest,
	): ResponseEntity<TransferResponse> {
		val transfer = transferService.create(request, idempotencyKey)
		return ResponseEntity.status(HttpStatus.CREATED).body(transfer)
	}

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<TransferResponse> =
		ResponseEntity.ok(transferService.get(id))

	@PostMapping("/{id}/reversals")
	fun reverse(
		@PathVariable id: UUID,
		@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) idempotencyKey: String,
		@Valid @RequestBody request: ReversalRequest,
	): ResponseEntity<TransferResponse> {
		val reversal = transferService.reverse(id, request.amount, idempotencyKey)
		return ResponseEntity.status(HttpStatus.CREATED).body(reversal)
	}
}
