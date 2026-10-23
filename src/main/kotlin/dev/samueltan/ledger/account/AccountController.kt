package dev.samueltan.ledger.account

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
}
