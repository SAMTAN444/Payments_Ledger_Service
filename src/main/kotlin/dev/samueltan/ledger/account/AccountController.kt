package dev.samueltan.ledger.account

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/accounts")
class AccountController(
	private val accountService: AccountService,
) {

	@PostMapping
	fun create(@Valid @RequestBody request: CreateAccountRequest): ResponseEntity<AccountResponse> {
		val account = accountService.create(request)
		return ResponseEntity.status(HttpStatus.CREATED).body(account)
	}

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<AccountResponse> {
		val account = accountService.get(id) ?: return ResponseEntity.notFound().build()
		return ResponseEntity.ok(account)
	}

	@GetMapping("/{id}/balance")
	fun getBalance(@PathVariable id: UUID): ResponseEntity<BalanceResponse> {
		val balance = accountService.getBalance(id) ?: return ResponseEntity.notFound().build()
		return ResponseEntity.ok(balance)
	}

	@GetMapping("/{id}/entries")
	fun getEntries(
		@PathVariable id: UUID,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
		@RequestParam(required = false) before: Long?,
	): ResponseEntity<EntriesPageResponse> =
		ResponseEntity.ok(accountService.getEntries(id, limit, before))
}
