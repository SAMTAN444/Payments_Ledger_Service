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
	fun create(request: TransferRequest, idempotencyKey: String): TransferResponse =
		moveMoney(
			fromAccountId = request.fromAccountId,
			toAccountId = request.toAccountId,
			amount = request.amount,
			currency = request.currency,
			idempotencyKey = idempotencyKey,
		)

	@Transactional
	fun reverse(originalId: UUID, amount: Long, idempotencyKey: String): TransferResponse {
		// Original must exist and must not itself be a reversal.
		// Safe to check before the lock: transfers are never updated.
		val original = transferRepository.findById(originalId)
			?: throw NotFoundException("Transfer $originalId not found")
		if (original.reversesTransferId != null) {
			throw RuleViolationException("Cannot reverse a reversal")
		}

		return moveMoney(
			fromAccountId = original.toAccountId,
			toAccountId = original.fromAccountId,
			amount = amount,
			currency = original.currency,
			idempotencyKey = idempotencyKey,
			reversesTransferId = original.id,
			checksAfterLock = {
				// Runs AFTER the lock, so concurrent refunds see each other's committed reversals.
				val remaining = original.amount - transferRepository.sumOfReversals(original.id)
				if (amount > remaining) {
					throw RuleViolationException("Reversal exceeds the remaining amount")
				}
			},
		)
	}

	fun get(id: UUID): TransferResponse =
		transferRepository.findById(id)
			?: throw NotFoundException("Transfer $id not found")

	private fun moveMoney(
		fromAccountId: UUID,
		toAccountId: UUID,
		amount: Long,
		currency: String,
		idempotencyKey: String,
		reversesTransferId: UUID? = null,
		checksAfterLock: () -> Unit = {},
	): TransferResponse {
		// 1. Lock and load both accounts (always in id order)
		val accounts = accountRepository.findTwoForUpdate(fromAccountId, toAccountId)

		// 2. Has this key been used? Must run AFTER the lock, so concurrent retries wait and see the first result
		val existing = transferRepository.findByIdempotencyKey(idempotencyKey)
		if (existing != null) {
			val sameRequest = existing.fromAccountId == fromAccountId &&
				existing.toAccountId == toAccountId &&
				existing.amount == amount &&
				existing.currency == currency &&
				existing.reversesTransferId == reversesTransferId
			if (sameRequest) {
				return existing
			}
			throw RuleViolationException(KEY_REUSED_MESSAGE)
		}

		// Extra checks from the caller (e.g. the reversal limit), run while holding the lock
		checksAfterLock()

		// 3. Both accounts must exist
		val from = accounts.find { it.id == fromAccountId }
			?: throw NotFoundException("Account $fromAccountId not found")
		val to = accounts.find { it.id == toAccountId }
			?: throw NotFoundException("Account $toAccountId not found")

		// 4. Currency must match both accounts
		if (from.currency != currency || to.currency != currency) {
			throw RuleViolationException("Currency $currency does not match both accounts")
		}

		// 5. CUSTOMER accounts can't go below 0. SYSTEM accounts can
		if (from.kind == "CUSTOMER") {
			val balance = accountRepository.balanceOf(from.id)
			if (balance < amount) {
				throw RuleViolationException("Insufficient funds")
			}
		}

		// 6. All checks passed: write the transfer and its two entries
		val transfer = try {
			transferRepository.insertTransfer(
				id = UUID.randomUUID(),
				fromAccountId = from.id,
				toAccountId = to.id,
				amount = amount,
				currency = currency,
				idempotencyKey = idempotencyKey,
				reversesTransferId = reversesTransferId,
			)
		} catch (e: DuplicateKeyException) {
			throw RuleViolationException(KEY_REUSED_MESSAGE)
		}
		transferRepository.insertEntry(transfer.id, from.id, -amount)
		transferRepository.insertEntry(transfer.id, to.id, amount)

		return transfer
	}
}
