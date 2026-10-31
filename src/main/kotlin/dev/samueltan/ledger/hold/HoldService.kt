package dev.samueltan.ledger.hold

import dev.samueltan.ledger.account.AccountRepository
import dev.samueltan.ledger.common.NotFoundException
import dev.samueltan.ledger.common.RuleViolationException
import dev.samueltan.ledger.transfer.TransferRepository
import dev.samueltan.ledger.transfer.TransferResponse
import dev.samueltan.ledger.transfer.TransferService
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val KEY_REUSED_MESSAGE = "Idempotency-Key was already used for a different request"

private const val STATUS_ACTIVE = "ACTIVE"
private const val STATUS_CAPTURED = "CAPTURED"
private const val STATUS_RELEASED = "RELEASED"

@Service
class HoldService(
	private val holdRepository: HoldRepository,
	private val accountRepository: AccountRepository,
	private val transferRepository: TransferRepository,
	private val transferService: TransferService,
) {
	@Transactional
	fun create(request: HoldRequest, idempotencyKey: String): HoldResponse {
		// 1. Lock and load both accounts (same lock as transfers, always in id order)
		val accounts = accountRepository.findTwoForUpdate(request.fromAccountId, request.toAccountId)

		// 2. Has this key been used? AFTER the lock, so concurrent retries wait and see the first result
		val existing = holdRepository.findByIdempotencyKey(idempotencyKey)
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

		// 5. A CUSTOMER can only hold money that's AVAILABLE (balance minus other ACTIVE holds)
		if (from.kind == "CUSTOMER") {
			val available = accountRepository.availableBalanceOf(from.id)
			if (available < request.amount) {
				throw RuleViolationException("Insufficient funds")
			}
		}

		// 6. All checks passed: save the hold as ACTIVE. No entries: no money moves.
		return try {
			holdRepository.insert(
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
	}

	@Transactional
	fun capture(holdId: UUID, amount: Long): TransferResponse {
		// 1. Lock the hold row. A concurrent capture or release of this hold waits here.
		val hold = holdRepository.findByIdForUpdate(holdId)
			?: throw NotFoundException("Hold $holdId not found")
		val captureKey = "capture:$holdId"

		// 2. Already captured: a retry (same amount) gets the same transfer, otherwise 422
		if (hold.status == STATUS_CAPTURED) {
			val previous = transferRepository.findByIdempotencyKey(captureKey)
			if (previous != null && previous.amount == amount) {
				return previous
			}
			throw RuleViolationException("Hold already captured")
		}

		// 3. Released holds can't be captured
		if (hold.status == STATUS_RELEASED) {
			throw RuleViolationException("Hold was released")
		}

		// 4. Can capture up to the hold amount, not more
		if (amount > hold.amount) {
			throw RuleViolationException("Capture exceeds the hold amount")
		}

		// 5. Mark CAPTURED FIRST, so the hold stops counting against "available"
		//    before moveMoney's funds check. Same transaction: if moveMoney throws, this rolls back.
		holdRepository.updateStatus(hold.id, STATUS_CAPTURED)

		// 6. Move the money: an ordinary transfer with two entries
		return transferService.moveMoney(
			fromAccountId = hold.fromAccountId,
			toAccountId = hold.toAccountId,
			amount = amount,
			currency = hold.currency,
			idempotencyKey = captureKey,
		)
	}

	@Transactional
	fun release(holdId: UUID): HoldResponse {
		// Lock the hold row, so release and capture of the same hold go one at a time
		val hold = holdRepository.findByIdForUpdate(holdId)
			?: throw NotFoundException("Hold $holdId not found")

		return when (hold.status) {
			STATUS_ACTIVE -> {
				holdRepository.updateStatus(hold.id, STATUS_RELEASED)
				hold.copy(status = STATUS_RELEASED)
			}
			STATUS_RELEASED -> hold  // a repeat: already released, nothing to do
			else -> throw RuleViolationException("Hold already captured")
		}
	}

	fun get(id: UUID): HoldResponse =
		holdRepository.findById(id)
			?: throw NotFoundException("Hold $id not found")
}
