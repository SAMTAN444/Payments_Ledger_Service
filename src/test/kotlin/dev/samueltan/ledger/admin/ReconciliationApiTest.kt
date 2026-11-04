package dev.samueltan.ledger.admin

import com.jayway.jsonpath.JsonPath
import dev.samueltan.ledger.common.PostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@AutoConfigureMockMvc
class ReconciliationApiTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var jdbcTemplate: JdbcTemplate

	// ---------- Tests ----------

	@Test
	fun `healthy ledger reconciles`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")
		transfer(cashIn, samuel, 10000).andExpect { status { isCreated() } }

		// a transfer and a partial reversal of it
		val paymentId = createdId(transfer(samuel, alice, 3000))
		createdId(postJson("/transfers/$paymentId/reversals", """{"amount":1000}""", key = true))

		// a captured hold and a released hold
		val capturedHoldId = createdId(hold(samuel, alice, 2000))
		createdId(postJson("/holds/$capturedHoldId/capture", """{"amount":2000}"""))
		val releasedHoldId = createdId(hold(samuel, alice, 500))
		mockMvc.post("/holds/$releasedHoldId/release").andExpect { status { isOk() } }

		val json = reconciliation()

		val problems = JsonPath.read<List<String>>(json, "$.problems")
		assertTrue(JsonPath.read<Boolean>(json, "$.ok"), "problems were $problems")
		assertTrue(problems.isEmpty(), "problems were $problems")
	}

	@Test
	@Transactional  // everything in this test, including the corruption, is rolled back at the end
	fun `corrupted ledger is reported`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val transferId = createdId(transfer(cashIn, samuel, 10000))

		// Break the ledger on purpose: a third entry for this transfer, which nothing balances.
		jdbcTemplate.update(
			"INSERT INTO entries (transfer_id, account_id, amount) VALUES (?, ?, ?)",
			UUID.fromString(transferId), UUID.fromString(samuel), 1L,
		)

		val json = reconciliation()

		val problems = JsonPath.read<List<String>>(json, "$.problems")
		assertFalse(JsonPath.read<Boolean>(json, "$.ok"), "problems were $problems")
		assertTrue(problems.any { it.contains(transferId) }, "problems were $problems")
		assertTrue(problems.any { it.startsWith("The ledger does not sum to zero") }, "problems were $problems")
	}

	// ---------- Helpers ----------

	private fun reconciliation(): String =
		mockMvc.get("/admin/reconciliation")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString

	private fun transfer(from: String, to: String, amount: Long): ResultActionsDsl =
		postJson(
			"/transfers",
			"""{"fromAccountId":"$from","toAccountId":"$to","amount":$amount,"currency":"SGD"}""",
			key = true,
		)

	private fun hold(from: String, to: String, amount: Long): ResultActionsDsl =
		postJson(
			"/holds",
			"""{"fromAccountId":"$from","toAccountId":"$to","amount":$amount,"currency":"SGD"}""",
			key = true,
		)

	// POST a JSON body, optionally with a fresh random Idempotency-Key
	private fun postJson(url: String, body: String, key: Boolean = false): ResultActionsDsl =
		mockMvc.post(url) {
			contentType = MediaType.APPLICATION_JSON
			if (key) {
				header("Idempotency-Key", UUID.randomUUID().toString())
			}
			content = body
		}

	// Expects 201 and returns the created resource's id
	private fun createdId(result: ResultActionsDsl): String {
		val json = result.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return JsonPath.read(json, "$.id")
	}

	// Copied from the other test classes
	private fun createAccount(ownerName: String, kind: String, currency: String = "SGD"): String {
		val json = mockMvc.post("/accounts") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"ownerName":"$ownerName","currency":"$currency","kind":"$kind"}"""
		}.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return JsonPath.read(json, "$.id")
	}
}
