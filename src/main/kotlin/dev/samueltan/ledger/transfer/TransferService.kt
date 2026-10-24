package dev.samueltan.ledger.transfer

import dev.samueltan.ledger.account.AccountRepository
import dev.samueltan.ledger.common.NotFoundException
import dev.samueltan.ledger.common.RuleViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TransferService(
	private val transferRepository: TransferRepository,
	private val accountRepository: AccountRepository,
) {
	@Transactional
	fun create(request: TransferRequest): TransferResponse {
		// 1. Lock and load both accounts (always in id order)
		val accounts = accountRepository.findTwoForUpdate(request.fromAccountId, request.toAccountId)

		// 2. Both accounts must exist
		val from = accounts.find { it.id == request.fromAccountId }
			?: throw NotFoundException("Account ${request.fromAccountId} not found")
		val to = accounts.find { it.id == request.toAccountId }
			?: throw NotFoundException("Account ${request.toAccountId} not found")

		// 3. Currency must match both accounts
		if (from.currency != request.currency || to.currency != request.currency) {
			throw RuleViolationException("Currency ${request.currency} does not match both accounts")
		}

		// 4. CUSTOMER accounts can't go below 0. SYSTEM accounts can
		if (from.kind == "CUSTOMER") {
			val balance = accountRepository.balanceOf(from.id)
			if (balance < request.amount) {
				throw RuleViolationException("Insufficient funds")
			}
		}

		// 5. All checks passed: write the transfer and its two entries
		val transfer = transferRepository.insertTransfer(
			id = UUID.randomUUID(),
			fromAccountId = from.id,
			toAccountId = to.id,
			amount = request.amount,
			currency = request.currency,
		)
		transferRepository.insertEntry(transfer.id, from.id, -request.amount)
		transferRepository.insertEntry(transfer.id, to.id, request.amount)

		return transfer
	}
}
