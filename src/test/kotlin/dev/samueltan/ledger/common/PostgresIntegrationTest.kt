package dev.samueltan.ledger.common

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for integration tests that need a real Postgres.
 *
 * One container is started for the whole test run (singleton pattern) and shared by every
 * subclass, so Spring's cached application context always points at a live database.
 * @ServiceConnection tells Spring Boot to point the datasource at this container.
 */
@SpringBootTest
abstract class PostgresIntegrationTest {

	companion object {
		@JvmField
		@ServiceConnection
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16")).apply { start() }
	}
}
