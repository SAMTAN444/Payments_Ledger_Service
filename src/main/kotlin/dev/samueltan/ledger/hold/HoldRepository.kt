package dev.samueltan.ledger.hold

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class HoldRepository(
	private val jdbcTemplate: JdbcTemplate,
) {
	// One list of columns, shared by every query, so the rowMapper and the queries can't drift apart.
	private val columns = "id, from_account_id, to_account_id, amount, currency, status, created_at"

	private val rowMapper = RowMapper<HoldResponse> { rs, _ ->
		HoldResponse(
			id = rs.getObject("id", UUID::class.java),
			fromAccountId = rs.getObject("from_account_id", UUID::class.java),
			toAccountId = rs.getObject("to_account_id", UUID::class.java),
			amount = rs.getLong("amount"),
			currency = rs.getString("currency"),
			status = rs.getString("status"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
		)
	}

	fun insert(
		id: UUID,
		fromAccountId: UUID,
		toAccountId: UUID,
		amount: Long,
		currency: String,
		idempotencyKey: String,
	): HoldResponse =
		jdbcTemplate.query(
			"""
			INSERT INTO holds (id, from_account_id, to_account_id, amount, currency, status, idempotency_key)
			VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
			RETURNING $columns
			""",
			rowMapper,
			id, fromAccountId, toAccountId, amount, currency, idempotencyKey,
		).single()

	fun findById(id: UUID): HoldResponse? =
		jdbcTemplate.query(
			"SELECT $columns FROM holds WHERE id = ?",
			rowMapper,
			id,
		).firstOrNull()

	// Locks the hold row until the transaction ends. Must run inside a @Transactional method.
	fun findByIdForUpdate(id: UUID): HoldResponse? =
		jdbcTemplate.query(
			"SELECT $columns FROM holds WHERE id = ? FOR UPDATE",
			rowMapper,
			id,
		).firstOrNull()

	fun findByIdempotencyKey(idempotencyKey: String): HoldResponse? =
		jdbcTemplate.query(
			"SELECT $columns FROM holds WHERE idempotency_key = ?",
			rowMapper,
			idempotencyKey,
		).firstOrNull()

	fun updateStatus(id: UUID, status: String) {
		jdbcTemplate.update(
			"UPDATE holds SET status = ? WHERE id = ?",
			status, id,
		)
	}
}
