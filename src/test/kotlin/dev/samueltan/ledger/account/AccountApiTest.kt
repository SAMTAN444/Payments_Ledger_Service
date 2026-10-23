package dev.samueltan.ledger.account

import java.util.UUID
import com.jayway.jsonpath.JsonPath
import dev.samueltan.ledger.common.PostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class AccountApiTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc : MockMvc

	@Test
	fun `create and fetch account`() {
		val createResult = mockMvc.post("/accounts") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"ownerName":"Samuel","currency":"SGD","kind":"CUSTOMER"}"""
		}.andExpect {
			status { isCreated() }
			jsonPath("$.id") { exists() }
			jsonPath("$.ownerName") { value("Samuel") }
			jsonPath("$.currency") { value("SGD") }
			jsonPath("$.kind") { value("CUSTOMER") }
			jsonPath("$.createdAt") { exists() }
		}.andReturn()

		val createdJson = createResult.response.contentAsString
		val id = JsonPath.read<String>(createdJson, "$.id")
		val createdAt = JsonPath.read<String>(createdJson, "$.createdAt")

		mockMvc.get("/accounts/$id").andExpect {
			status { isOk() }
			jsonPath("$.id") { value(id) }
			jsonPath("$.ownerName") { value("Samuel") }
			jsonPath("$.currency") { value("SGD") }
			jsonPath("$.kind") { value("CUSTOMER") }
			jsonPath("$.createdAt") { value(createdAt) }
		}
	}

	@Test
	fun `create with lowercase currency returns 400`() {
		mockMvc.post("/accounts") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"ownerName":"Samuel","currency":"sgd","kind":"CUSTOMER"}"""
		}.andExpect {
			status { isBadRequest() }
		}
	}

	@Test
	fun `get unknown account returns 404`() {
		mockMvc.get("/accounts/${UUID.randomUUID()}").andExpect {
			status { isNotFound() }
		}
	}
}