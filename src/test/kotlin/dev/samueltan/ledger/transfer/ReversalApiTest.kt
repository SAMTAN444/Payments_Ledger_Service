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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
class ReversalApiTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc: MockMvc

	// ---------- Tests ----------

	@Test
	fun `full refund moves the money back`() {
		val (_, samuel, alice, paymentId) = samuelPaysAlice()

		val reversalJson = reverse(paymentId, 3000).andExpect {
			status { isCreated() }
			jsonPath("$.fromAccountId") { value(alice) }
			jsonPath("$.toAccountId") { value(samuel) }
			jsonPath("$.amount") { value(3000) }
			jsonPath("$.reversesTransferId") { value(paymentId) }
		}.andReturn().response.contentAsString
		val reversalId = JsonPath.read<String>(reversalJson, "$.id")

		assertEquals(10000L, balanceOf(samuel))
		assertEquals(0L, balanceOf(alice))

		mockMvc.get("/transfers/$reversalId").andExpect {
			status { isOk() }
			jsonPath("$.reversesTransferId") { value(paymentId) }
		}
	}

	@Test
	fun `partial refunds are allowed up to the original amount`() {
		val (_, samuel, alice, paymentId) = samuelPaysAlice()

		reverse(paymentId, 1000).andExpect { status { isCreated() } }
		reverse(paymentId, 2000).andExpect { status { isCreated() } }
		reverse(paymentId, 1).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Reversal exceeds the remaining amount") }
		}

		assertEquals(10000L, balanceOf(samuel))
		assertEquals(0L, balanceOf(alice))
	}

	@Test
	fun `a single refund larger than the original is rejected`() {
		val (_, samuel, alice, paymentId) = samuelPaysAlice()

		reverse(paymentId, 3001).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Reversal exceeds the remaining amount") }
		}

		assertEquals(7000L, balanceOf(samuel))
		assertEquals(3000L, balanceOf(alice))
	}

	@Test
	fun `a reversal cannot be reversed`() {
		val (_, _, _, paymentId) = samuelPaysAlice()
		val reversalId = createdId(reverse(paymentId, 1000))

		reverse(reversalId, 500).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Cannot reverse a reversal") }
		}
	}

	@Test
	fun `reversing an unknown transfer returns 404`() {
		reverse(UUID.randomUUID().toString(), 100).andExpect {
			status { isNotFound() }
		}
	}

	@Test
	fun `refund fails if the receiver already spent the money`() {
		val (_, samuel, alice, paymentId) = samuelPaysAlice()
		val bob = createAccount("Bob", "CUSTOMER")
		transfer(alice, bob, 3000).andExpect { status { isCreated() } }

		reverse(paymentId, 3000).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Insufficient funds") }
		}

		assertEquals(7000L, balanceOf(samuel))
		assertEquals(0L, balanceOf(alice))
		assertEquals(3000L, balanceOf(bob))
	}

	@Test
	fun `retrying a refund with the same key returns the same reversal`() {
		val (_, samuel, alice, paymentId) = samuelPaysAlice()
		val key = UUID.randomUUID().toString()

		val firstId = createdId(reverse(paymentId, 1000, key = key))
		val secondId = createdId(reverse(paymentId, 1000, key = key))

		assertEquals(firstId, secondId)
		assertEquals(8000L, balanceOf(samuel))
		assertEquals(2000L, balanceOf(alice))
	}

	@Test
	fun `concurrent partial refunds never exceed the original`() {
		val (cashIn, samuel, alice, paymentId) = samuelPaysAlice()
		// Give Alice plenty of money, so ONLY the "remaining amount" rule can stop refunds.
		transfer(cashIn, alice, 10000).andExpect { status { isCreated() } }

		val threadCount = 10
		val executor = Executors.newFixedThreadPool(threadCount)
		val startGate = CountDownLatch(1)

		val futures = (1..threadCount).map {
			executor.submit(Callable {
				startGate.await()
				reverse(paymentId, 1000).andReturn().response.status
			})
		}

		startGate.countDown()
		val statuses = futures.map { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		assertEquals(3, statuses.count { it == 201 }, "statuses were $statuses")
		assertEquals(7, statuses.count { it == 422 }, "statuses were $statuses")
		assertEquals(10000L, balanceOf(samuel))
		assertEquals(10000L, balanceOf(alice))
	}

	// ---------- Helpers ----------

	private data class Payment(
		val cashIn: String,
		val samuel: String,
		val alice: String,
		val paymentId: String,
	)

	// cashIn funds Samuel with 10000, then Samuel pays Alice `amount`.
	private fun samuelPaysAlice(amount: Long = 3000): Payment {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")
		transfer(cashIn, samuel, 10000).andExpect { status { isCreated() } }
		val paymentId = createdId(transfer(samuel, alice, amount))
		return Payment(cashIn = cashIn, samuel = samuel, alice = alice, paymentId = paymentId)
	}

	private fun reverse(
		transferId: String,
		amount: Long,
		key: String? = UUID.randomUUID().toString(),
	): ResultActionsDsl =
		mockMvc.post("/transfers/$transferId/reversals") {
			contentType = MediaType.APPLICATION_JSON
			if (key != null) {
				header("Idempotency-Key", key)
			}
			content = """{"amount":$amount}"""
		}

	// Expects 201 and returns the created transfer's id.
	private fun createdId(result: ResultActionsDsl): String {
		val json = result.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return JsonPath.read(json, "$.id")
	}

	// Copied from TransferApiTest
	private fun createAccount(ownerName: String, kind: String, currency: String = "SGD"): String {
		val json = mockMvc.post("/accounts") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"ownerName":"$ownerName","currency":"$currency","kind":"$kind"}"""
		}.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return JsonPath.read(json, "$.id")
	}

	// Copied from TransferApiTest
	private fun transfer(
		from: String,
		to: String,
		amount: Long,
		currency: String = "SGD",
		key: String? = UUID.randomUUID().toString(),
	): ResultActionsDsl =
		mockMvc.post("/transfers") {
			contentType = MediaType.APPLICATION_JSON
			if (key != null) {
				header("Idempotency-Key", key)
			}
			content = """{"fromAccountId":"$from","toAccountId":"$to","amount":$amount,"currency":"$currency"}"""
		}

	// Copied from TransferApiTest
	private fun balanceOf(accountId: String): Long {
		val json = mockMvc.get("/accounts/$accountId/balance")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString
		return JsonPath.read<Number>(json, "$.balance").toLong()
	}
}
