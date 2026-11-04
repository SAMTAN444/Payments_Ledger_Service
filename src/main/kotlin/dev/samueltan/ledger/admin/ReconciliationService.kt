package dev.samueltan.ledger.admin

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReconciliationService(
	private val reconciliationRepository: ReconciliationRepository,
) {
	private val log = LoggerFactory.getLogger(ReconciliationService::class.java)

	fun run(): ReconciliationResponse {
		val problems = reconciliationRepository.ledgerNotBalanced() +
			reconciliationRepository.badTransferEntries() +
			reconciliationRepository.badReversals() +
			reconciliationRepository.badCaptures() +
			reconciliationRepository.negativeCustomers()

		if (problems.isNotEmpty()) {
			log.warn("Reconciliation found {} problem(s): {}", problems.size, problems)
		}

		return ReconciliationResponse(ok = problems.isEmpty(), problems = problems)
	}
}
