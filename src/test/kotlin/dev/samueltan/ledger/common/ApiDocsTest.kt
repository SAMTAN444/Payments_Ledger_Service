package dev.samueltan.ledger.common

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class ApiDocsTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Test
	fun `OpenAPI document is served and lists the endpoints`() {
		mockMvc.get("/v3/api-docs").andExpect {
			status { isOk() }
			jsonPath("$.paths['/transfers']") { exists() }
			jsonPath("$.paths['/holds/{id}/capture']") { exists() }
		}
	}
}
