package dev.samueltan.ledger.common

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class DemoPageTest : PostgresIntegrationTest() {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Test
	fun `the site root serves the demo page`() {
		mockMvc.get("/").andExpect {
			status { isOk() }
			forwardedUrl("index.html")
		}
		mockMvc.get("/index.html").andExpect {
			status { isOk() }
			content { string(containsString("Fake money")) }
		}
	}
}
