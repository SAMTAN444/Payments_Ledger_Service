package dev.samueltan.ledger.hold

import dev.samueltan.ledger.transfer.TransferResponse
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
@RequestMapping("/holds")
class HoldController(
	private val holdService: HoldService,
) {
	@PostMapping
	fun create(
		@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) idempotencyKey: String,
		@Valid @RequestBody request: HoldRequest,
	): ResponseEntity<HoldResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(holdService.create(request, idempotencyKey))

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<HoldResponse> =
		ResponseEntity.ok(holdService.get(id))

	@PostMapping("/{id}/capture")
	fun capture(
		@PathVariable id: UUID,
		@Valid @RequestBody request: CaptureRequest,
	): ResponseEntity<TransferResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(holdService.capture(id, request.amount))

	@PostMapping("/{id}/release")
	fun release(@PathVariable id: UUID): ResponseEntity<HoldResponse> =
		ResponseEntity.ok(holdService.release(id))
}
