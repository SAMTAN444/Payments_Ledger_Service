package dev.samueltan.ledger.hold

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
class HoldApiTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc: MockMvc

	// ---------- Tests ----------

	@Test
	fun `a hold reduces available but not balance`() {
		val (samuel, alice) = samuelWith10000()
		val bob = createAccount("Bob", "CUSTOMER")

		hold(samuel, alice, 4000).andExpect { status { isCreated() } }

		assertEquals(10000L, balanceOf(samuel))
		assertEquals(6000L, availableOf(samuel))
		transfer(samuel, bob, 7000).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Insufficient funds") }
		}
	}

	@Test
	fun `creating a hold larger than available is rejected`() {
		val (samuel, alice) = samuelWith10000()
		hold(samuel, alice, 4000).andExpect { status { isCreated() } }

		// 7000 is less than the balance (10000) but more than available (6000)
		hold(samuel, alice, 7000).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Insufficient funds") }
		}
		assertEquals(6000L, availableOf(samuel))
	}

	@Test
	fun `full capture moves the money and marks the hold captured`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))

		capture(holdId, 4000).andExpect {
			status { isCreated() }
			jsonPath("$.fromAccountId") { value(samuel) }
			jsonPath("$.toAccountId") { value(alice) }
			jsonPath("$.amount") { value(4000) }
		}

		assertEquals(6000L, balanceOf(samuel))
		assertEquals(6000L, availableOf(samuel))
		assertEquals(4000L, balanceOf(alice))
		assertEquals("CAPTURED", holdStatus(holdId))
	}

	@Test
	fun `capturing a hold of the whole balance works`() {
		// Not in the spec's list: proves the status is updated BEFORE moveMoney's funds check.
		// With the wrong order, available would be 10000 - 10000 = 0 and this capture would fail.
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 10000))

		capture(holdId, 10000).andExpect { status { isCreated() } }

		assertEquals(0L, balanceOf(samuel))
		assertEquals(10000L, balanceOf(alice))
	}

	@Test
	fun `partial capture frees the rest of the hold`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))

		capture(holdId, 2500).andExpect { status { isCreated() } }

		assertEquals(7500L, balanceOf(samuel))
		assertEquals(7500L, availableOf(samuel))
		assertEquals(2500L, balanceOf(alice))
	}

	@Test
	fun `release frees the money and cannot be followed by a capture`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))

		release(holdId).andExpect {
			status { isOk() }
			jsonPath("$.status") { value("RELEASED") }
		}
		assertEquals(10000L, balanceOf(samuel))
		assertEquals(10000L, availableOf(samuel))

		capture(holdId, 4000).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Hold was released") }
		}
		release(holdId).andExpect {
			status { isOk() }
			jsonPath("$.status") { value("RELEASED") }
		}
		assertEquals(0L, balanceOf(alice))
	}

	@Test
	fun `release after capture is rejected`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))
		capture(holdId, 4000).andExpect { status { isCreated() } }

		release(holdId).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Hold already captured") }
		}
		assertEquals("CAPTURED", holdStatus(holdId))
	}

	@Test
	fun `capture retry returns the same transfer and moves money once and a different capture is rejected`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))

		val firstId = createdId(capture(holdId, 4000))
		val secondId = createdId(capture(holdId, 4000))

		assertEquals(firstId, secondId)
		assertEquals(6000L, balanceOf(samuel))
		assertEquals(4000L, balanceOf(alice))

		capture(holdId, 3000).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Hold already captured") }
		}
	}

	@Test
	fun `creating a hold with the same key twice returns the same hold`() {
		val (samuel, alice) = samuelWith10000()
		val key = UUID.randomUUID().toString()

		val firstId = createdId(hold(samuel, alice, 4000, key = key))
		val secondId = createdId(hold(samuel, alice, 4000, key = key))

		assertEquals(firstId, secondId)
		assertEquals(6000L, availableOf(samuel))
	}

	@Test
	fun `capture more than the hold is rejected`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))

		capture(holdId, 4001).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Capture exceeds the hold amount") }
		}
		assertEquals("ACTIVE", holdStatus(holdId))
		assertEquals(10000L, balanceOf(samuel))
	}

	@Test
	fun `unknown hold returns 404`() {
		val unknown = UUID.randomUUID().toString()

		mockMvc.get("/holds/$unknown").andExpect { status { isNotFound() } }
		capture(unknown, 100).andExpect { status { isNotFound() } }
		release(unknown).andExpect { status { isNotFound() } }
	}

	@Test
	fun `concurrent capture and release of one hold - only one wins`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))

		val threadCount = 10
		val executor = Executors.newFixedThreadPool(threadCount)
		val startGate = CountDownLatch(1)

		val futures = (1..threadCount).map { i ->
			executor.submit(Callable {
				startGate.await()
				if (i % 2 == 0) {
					"capture" to capture(holdId, 4000).andReturn().response.status
				} else {
					"release" to release(holdId).andReturn().response.status
				}
			})
		}

		startGate.countDown()
		val results = futures.map { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		when (val finalStatus = holdStatus(holdId)) {
			"CAPTURED" -> {
				assertEquals(5, results.count { it == ("capture" to 201) }, "results were $results")
				assertEquals(5, results.count { it == ("release" to 422) }, "results were $results")
				assertEquals(4000L, balanceOf(alice))
				assertEquals(6000L, balanceOf(samuel))
				assertEquals(6000L, availableOf(samuel))
			}
			"RELEASED" -> {
				assertEquals(5, results.count { it == ("release" to 200) }, "results were $results")
				assertEquals(5, results.count { it == ("capture" to 422) }, "results were $results")
				assertEquals(0L, balanceOf(alice))
				assertEquals(10000L, balanceOf(samuel))
				assertEquals(10000L, availableOf(samuel))
			}
			else -> throw AssertionError("Unexpected hold status $finalStatus")
		}
	}

	@Test
	fun `concurrent holds never exceed available`() {
		val (samuel, alice) = samuelWith10000()

		val threadCount = 10
		val executor = Executors.newFixedThreadPool(threadCount)
		val startGate = CountDownLatch(1)

		val futures = (1..threadCount).map {
			executor.submit(Callable {
				startGate.await()
				hold(samuel, alice, 2000).andReturn().response.status
			})
		}

		startGate.countDown()
		val statuses = futures.map { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		assertEquals(5, statuses.count { it == 201 }, "statuses were $statuses")
		assertEquals(5, statuses.count { it == 422 }, "statuses were $statuses")
		assertEquals(0L, availableOf(samuel))
		assertEquals(10000L, balanceOf(samuel))
	}

	@Test
	fun `clients cannot use the reserved capture key prefix`() {
		val (samuel, alice) = samuelWith10000()
		val holdId = createdId(hold(samuel, alice, 4000))

		transfer(samuel, alice, 1, key = "capture:$holdId").andExpect { status { isBadRequest() } }
		capture(holdId, 4000).andExpect { status { isCreated() } }
	}

	// ---------- Helpers ----------

	private data class Parties(
		val samuel: String,
		val alice: String,
	)

	// cashIn funds Samuel with 10000. Alice is the merchant, with 0.
	private fun samuelWith10000(): Parties {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")
		transfer(cashIn, samuel, 10000).andExpect { status { isCreated() } }
		return Parties(samuel = samuel, alice = alice)
	}

	private fun hold(
		from: String,
		to: String,
		amount: Long,
		key: String? = UUID.randomUUID().toString(),
	): ResultActionsDsl =
		mockMvc.post("/holds") {
			contentType = MediaType.APPLICATION_JSON
			if (key != null) {
				header("Idempotency-Key", key)
			}
			content = """{"fromAccountId":"$from","toAccountId":"$to","amount":$amount,"currency":"SGD"}"""
		}

	private fun capture(holdId: String, amount: Long): ResultActionsDsl =
		mockMvc.post("/holds/$holdId/capture") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"amount":$amount}"""
		}

	private fun release(holdId: String): ResultActionsDsl =
		mockMvc.post("/holds/$holdId/release")

	private fun holdStatus(holdId: String): String {
		val json = mockMvc.get("/holds/$holdId")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString
		return JsonPath.read(json, "$.status")
	}

	private fun availableOf(accountId: String): Long {
		val json = mockMvc.get("/accounts/$accountId/balance")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString
		return JsonPath.read<Number>(json, "$.available").toLong()
	}

	// Expects 201 and returns the created resource's id (a hold or a transfer)
	private fun createdId(result: ResultActionsDsl): String {
		val json = result.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return JsonPath.read(json, "$.id")
	}

	// Copied from ReversalApiTest
	private fun createAccount(ownerName: String, kind: String, currency: String = "SGD"): String {
		val json = mockMvc.post("/accounts") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"ownerName":"$ownerName","currency":"$currency","kind":"$kind"}"""
		}.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return JsonPath.read(json, "$.id")
	}

	// Copied from ReversalApiTest
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

	// Copied from ReversalApiTest
	private fun balanceOf(accountId: String): Long {
		val json = mockMvc.get("/accounts/$accountId/balance")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString
		return JsonPath.read<Number>(json, "$.balance").toLong()
	}
}
