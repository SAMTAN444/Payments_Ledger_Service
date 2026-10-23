package dev.samueltan.ledger.account

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AccountService(
	private val accountRepository: AccountRepository,
) {

	fun create(request: CreateAccountRequest): AccountResponse = 
		accountRepository.insert(
			id = UUID.randomUUID(),
			ownerName = request.ownerName,
			currency = request.currency,
			kind = request.kind,
		)

	fun get(id: UUID): AccountResponse? = 
		accountRepository.findById(id)
}
