package dev.samueltan.ledger.transfer

import dev.samueltan.ledger.account.AccountRepository
import dev.samueltan.ledger.common.NotFoundException
import dev.samueltan.ledger.common.RuleViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val KEY_REUSED_MESSAGE = "Idempotency-Key was already used for a different request"

@Service
class TransferService(
	private val transferRepository: TransferRepository,
	private val accountRepository: AccountRepository,
) {
	@Transactional
	fun create(request: TransferRequest, idempotencyKey: String): TransferResponse {
		// 1. Lock and load both accounts (always in id order)
		val accounts = accountRepository.findTwoForUpdate(request.fromAccountId, request.toAccountId)

		// 2. Has this key been used? Must run AFTER the lock, so concurrent retries wait and see the first result
		val existing = transferRepository.findByIdempotencyKey(idempotencyKey)
		if (existing != null) {
			val sameRequest = existing.fromAccountId == request.fromAccountId &&
				existing.toAccountId == request.toAccountId &&
				existing.amount == request.amount &&
				existing.currency == request.currency
			if (sameRequest) {
				return existing
			}
			throw RuleViolationException(KEY_REUSED_MESSAGE)
		}

		// 3. Both accounts must exist
		val from = accounts.find { it.id == request.fromAccountId }
			?: throw NotFoundException("Account ${request.fromAccountId} not found")
		val to = accounts.find { it.id == request.toAccountId }
			?: throw NotFoundException("Account ${request.toAccountId} not found")

		// 4. Currency must match both accounts
		if (from.currency != request.currency || to.currency != request.currency) {
			throw RuleViolationException("Currency ${request.currency} does not match both accounts")
		}

		// 5. CUSTOMER accounts can't go below 0. SYSTEM accounts can
		if (from.kind == "CUSTOMER") {
			val balance = accountRepository.balanceOf(from.id)
			if (balance < request.amount) {
				throw RuleViolationException("Insufficient funds")
			}
		}

		// 6. All checks passed: write the transfer and its two entries
		val transfer = try {
			transferRepository.insertTransfer(
				id = UUID.randomUUID(),
				fromAccountId = from.id,
				toAccountId = to.id,
				amount = request.amount,
				currency = request.currency,
				idempotencyKey = idempotencyKey,
			)
		} catch (e: DuplicateKeyException) {
			throw RuleViolationException(KEY_REUSED_MESSAGE)
		}
		transferRepository.insertEntry(transfer.id, from.id, -request.amount)
		transferRepository.insertEntry(transfer.id, to.id, request.amount)

		return transfer
	}
}
