package dev.samueltan.ledger.transfer

import com.jayway.jsonpath.JsonPath
import dev.samueltan.ledger.common.PostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID

@AutoConfigureMockMvc
class TransferApiTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc: MockMvc

	// ---------- Tests ----------

	@Test
	fun `transfers move money and balances sum to zero`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")

		transfer(cashIn, samuel, 10000).andExpect {
			status { isCreated() }
			jsonPath("$.fromAccountId") { value(cashIn) }
			jsonPath("$.toAccountId") { value(samuel) }
		}
		transfer(samuel, alice, 1050).andExpect { status { isCreated() } }

		assertEquals(-10000L, balanceOf(cashIn))
		assertEquals(8950L, balanceOf(samuel))
		assertEquals(1050L, balanceOf(alice))
	}

	@Test
	fun `customer cannot overdraw but can spend exactly to zero`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")
		transfer(cashIn, samuel, 5000).andExpect { status { isCreated() } }

		transfer(samuel, alice, 5001).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Insufficient funds") }
		}
		assertEquals(5000L, balanceOf(samuel))
		assertEquals(0L, balanceOf(alice))

		transfer(samuel, alice, 5000).andExpect { status { isCreated() } }
		assertEquals(0L, balanceOf(samuel))
		assertEquals(5000L, balanceOf(alice))
	}

	@Test
	fun `transfer to unknown account returns 404`() {
		val samuel = createAccount("Samuel", "CUSTOMER")

		transfer(samuel, UUID.randomUUID().toString(), 100).andExpect {
			status { isNotFound() }
		}
	}

	@Test
	fun `currency must match both accounts`() {
		val samuel = createAccount("Samuel", "CUSTOMER")
		val bob = createAccount("Bob", "CUSTOMER", currency = "USD")

		transfer(samuel, bob, 100).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Currency SGD does not match both accounts") }
		}
	}

	@Test
	fun `invalid requests are rejected with 400`() {
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")

		transfer(samuel, samuel, 100).andExpect { status { isBadRequest() } }
		transfer(samuel, alice, 0).andExpect { status { isBadRequest() } }
	}

	@Test
	fun `same key and same body returns the original transfer and moves money once`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val key = UUID.randomUUID().toString()

		val firstJson = transfer(cashIn, samuel, 5000, key = key)
			.andExpect { status { isCreated() } }
			.andReturn().response.contentAsString
		val secondJson = transfer(cashIn, samuel, 5000, key = key)
			.andExpect { status { isCreated() } }
			.andReturn().response.contentAsString

		assertEquals(JsonPath.read<String>(firstJson, "$.id"), JsonPath.read<String>(secondJson, "$.id"))
		assertEquals(firstJson, secondJson)
		assertEquals(5000L, balanceOf(samuel))
		assertEquals(-5000L, balanceOf(cashIn))
	}

	@Test
	fun `same key with a different amount returns 422 and moves no money`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val key = UUID.randomUUID().toString()
		transfer(cashIn, samuel, 5000, key = key).andExpect { status { isCreated() } }

		transfer(cashIn, samuel, 6000, key = key).andExpect {
			status { isEqualTo(422) }
			jsonPath("$.detail") { value("Idempotency-Key was already used for a different request") }
		}

		assertEquals(5000L, balanceOf(samuel))
		assertEquals(-5000L, balanceOf(cashIn))
	}

	@Test
	fun `missing Idempotency-Key header returns 400`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")

		transfer(cashIn, samuel, 5000, key = null).andExpect { status { isBadRequest() } }

		assertEquals(0L, balanceOf(samuel))
	}

	@Test
	fun `get transfer by id returns it`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val createdJson = transfer(cashIn, samuel, 2500)
			.andExpect { status { isCreated() } }
			.andReturn().response.contentAsString
		val id = JsonPath.read<String>(createdJson, "$.id")

		mockMvc.get("/transfers/$id").andExpect {
			status { isOk() }
			jsonPath("$.id") { value(id) }
			jsonPath("$.fromAccountId") { value(cashIn) }
			jsonPath("$.toAccountId") { value(samuel) }
			jsonPath("$.amount") { value(2500) }
			jsonPath("$.currency") { value("SGD") }
		}
	}

	@Test
	fun `get unknown transfer returns 404`() {
		mockMvc.get("/transfers/${UUID.randomUUID()}").andExpect {
			status { isNotFound() }
		}
	}

	@Test
	fun `entries are newest first with signed amounts and counterparties`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")
		transfer(cashIn, samuel, 10000).andExpect { status { isCreated() } }
		transfer(samuel, alice, 1050).andExpect { status { isCreated() } }

		val json = entriesPage(samuel)

		val amounts = JsonPath.read<List<Number>>(json, "$.entries[*].amount").map { it.toLong() }
		assertEquals(listOf(-1050L, 10000L), amounts)
		val counterparties = JsonPath.read<List<String>>(json, "$.entries[*].counterpartyAccountId")
		assertEquals(listOf(alice, cashIn), counterparties)
		assertNull(JsonPath.read<Number?>(json, "$.nextCursor"))
	}

	@Test
	fun `entries paginate with a cursor without duplicates or gaps`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		repeat(5) {
			transfer(cashIn, samuel, 100).andExpect { status { isCreated() } }
		}

		val page1 = entriesPage(samuel, limit = 2)
		val cursor1 = JsonPath.read<Number>(page1, "$.nextCursor").toLong()
		val page2 = entriesPage(samuel, limit = 2, before = cursor1)
		val cursor2 = JsonPath.read<Number>(page2, "$.nextCursor").toLong()
		val page3 = entriesPage(samuel, limit = 2, before = cursor2)

		assertEquals(2, entryIds(page1).size)
		assertEquals(2, entryIds(page2).size)
		assertEquals(1, entryIds(page3).size)
		assertNull(JsonPath.read<Number?>(page3, "$.nextCursor"))

		val allIds = entryIds(page1) + entryIds(page2) + entryIds(page3)
		assertEquals(5, allIds.toSet().size, "ids were $allIds")
		assertEquals(allIds.sortedDescending(), allIds, "ids were $allIds")
	}

	@Test
	fun `entries limit must be between 1 and 100`() {
		val samuel = createAccount("Samuel", "CUSTOMER")

		mockMvc.get("/accounts/$samuel/entries") { param("limit", "0") }
			.andExpect { status { isBadRequest() } }
		mockMvc.get("/accounts/$samuel/entries") { param("limit", "101") }
			.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `entries of unknown account returns 404`() {
		mockMvc.get("/accounts/${UUID.randomUUID()}/entries").andExpect {
			status { isNotFound() }
		}
	}

	@Test
	fun `concurrent transfers never overdraw a customer`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val alice = createAccount("Alice", "CUSTOMER")
		transfer(cashIn, samuel, 10000).andExpect { status { isCreated() } }

		val threadCount = 20
		val executor = Executors.newFixedThreadPool(threadCount)
		val startGate = CountDownLatch(1)

		val futures = (1..threadCount).map {
			executor.submit(Callable {
				startGate.await()
				transfer(samuel, alice, 1000).andReturn().response.status
			})
		}

		startGate.countDown()
		val statuses = futures.map { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		assertEquals(10, statuses.count { it == 201 })
		assertEquals(10, statuses.count { it == 422 })
		assertEquals(0L, balanceOf(samuel))
		assertEquals(10000L, balanceOf(alice))
	}

	@Test
	fun `concurrent requests with the same key move money only once`() {
		val cashIn = createAccount("Cash In", "SYSTEM")
		val samuel = createAccount("Samuel", "CUSTOMER")
		val key = UUID.randomUUID().toString()

		val threadCount = 10
		val executor = Executors.newFixedThreadPool(threadCount)
		val startGate = CountDownLatch(1)

		val futures = (1..threadCount).map {
			executor.submit(Callable {
				startGate.await()
				transfer(cashIn, samuel, 5000, key = key).andReturn().response
			})
		}

		startGate.countDown()
		val responses = futures.map { it.get(30, TimeUnit.SECONDS) }
		executor.shutdown()

		val statuses = responses.map { it.status }
		assertEquals(threadCount, statuses.count { it == 201 }, "statuses were $statuses")

		val ids = responses.map { JsonPath.read<String>(it.contentAsString, "$.id") }.toSet()
		assertEquals(1, ids.size, "transfer ids were $ids")

		assertEquals(5000L, balanceOf(samuel))
		assertEquals(-5000L, balanceOf(cashIn))
	}

	// ---------- Helpers ----------

	private fun createAccount(ownerName: String, kind: String, currency: String = "SGD"): String {
		val json = mockMvc.post("/accounts") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"ownerName":"$ownerName","currency":"$currency","kind":"$kind"}"""
		}.andExpect { status { isCreated() } }.andReturn().response.contentAsString
		return JsonPath.read(json, "$.id")
	}

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

	private fun balanceOf(accountId: String): Long {
		val json = mockMvc.get("/accounts/$accountId/balance")
			.andExpect { status { isOk() } }
			.andReturn().response.contentAsString
		return JsonPath.read<Number>(json, "$.balance").toLong()
	}

	private fun entriesPage(accountId: String, limit: Int? = null, before: Long? = null): String =
		mockMvc.get("/accounts/$accountId/entries") {
			if (limit != null) param("limit", limit.toString())
			if (before != null) param("before", before.toString())
		}.andExpect { status { isOk() } }.andReturn().response.contentAsString

	private fun entryIds(pageJson: String): List<Long> =
		JsonPath.read<List<Number>>(pageJson, "$.entries[*].entryId").map { it.toLong() }
}
