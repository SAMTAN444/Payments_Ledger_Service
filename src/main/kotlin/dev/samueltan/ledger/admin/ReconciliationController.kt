package dev.samueltan.ledger.admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// shortcut: no authentication, so anyone who can reach the service can call this.
// Restrict /admin to operators once the service has login.
@RestController
@RequestMapping("/admin")
class ReconciliationController(
	private val reconciliationService: ReconciliationService,
) {
	@GetMapping("/reconciliation")
	fun reconcile(): ReconciliationResponse = reconciliationService.run()
}
