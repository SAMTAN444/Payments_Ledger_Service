package dev.samueltan.ledger.account

import dev.samueltan.ledger.common.NotFoundException
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

	fun getBalance(id: UUID): BalanceResponse? {
		val account = accountRepository.findById(id) ?: return null
		return BalanceResponse(
			accountId = account.id,
			currency = account.currency,
			balance = accountRepository.balanceOf(account.id),
			available = accountRepository.availableBalanceOf(account.id),
		)
	}

	fun getEntries(id: UUID, limit: Int, before: Long?): EntriesPageResponse {
		accountRepository.findById(id) ?: throw NotFoundException("Account $id not found")

		val rows = accountRepository.findEntries(id, before, limit + 1)
		val hasMore = rows.size > limit
		val entries = rows.take(limit)
		val nextCursor = if (hasMore) entries.last().entryId else null

		return EntriesPageResponse(entries = entries, nextCursor = nextCursor)
	}
}
