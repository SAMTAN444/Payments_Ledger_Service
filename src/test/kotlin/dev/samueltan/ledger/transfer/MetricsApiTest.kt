package dev.samueltan.ledger.transfer

import com.jayway.jsonpath.JsonPath
import dev.samueltan.ledger.common.PostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@AutoConfigureMockMvc
class MetricsApiTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Test
	fun `new transfers are counted but replays are not`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val key = UUID.randomUUID().toString()

		val before = transfersCreated("transfer")

		transfer(cashIn, samuel, 1000, key).andExpect { status { isCreated() } }
		val afterFirst = transfersCreated("transfer")

		transfer(cashIn, samuel, 1000, key).andExpect { status { isCreated() } }  // replay
		val afterReplay = transfersCreated("transfer")

		assertEquals(before + 1, afterFirst, "a new transfer must be counted once")
		assertEquals(afterFirst, afterReplay, "a replay must not be counted")
	}

	// ---------- Helpers ----------

	// Current value of ledger.transfers.created for one type tag, read through Actuator
	private fun transfersCreated(type: String): Double {
		val json = mockMvc.get("/actuator/metrics/ledger.transfers.created") {
			param("tag", "type:$type")
		}.andExpect { status { isOk() } }.andReturn().response.contentAsString
		return JsonPath.read<List<Number>>(json, "$.measurements[?(@.statistic == 'COUNT')].value")
			.single()
			.toDouble()
	}

	private fun transfer(from: String, to: String, amount: Long, key: String): ResultActionsDsl =
		mockMvc.post("/transfers") {
			contentType = MediaType.APPLICATION_JSON
			header("Idempotency-Key", key)
			content = """{"fromAccountId":"$from","toAccountId":"$to","amount":$amount,"currency":"SGD"}"""
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
