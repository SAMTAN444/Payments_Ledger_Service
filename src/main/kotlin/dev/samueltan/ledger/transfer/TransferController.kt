package dev.samueltan.ledger.transfer

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/transfers")
class TransferController(
	private val transferService: TransferService,
) {
	@PostMapping
	fun create(@Valid @RequestBody request: TransferRequest): ResponseEntity<TransferResponse> {
		val transfer = transferService.create(request)
		return ResponseEntity.status(HttpStatus.CREATED).body(transfer)
	}
}
