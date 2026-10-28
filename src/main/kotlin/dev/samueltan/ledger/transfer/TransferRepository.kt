package dev.samueltan.ledger.transfer

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TransferRepository(
	private val jdbcTemplate: JdbcTemplate,
) {
	private val rowMapper = RowMapper<TransferResponse> { rs, _ ->
		TransferResponse(
			id = rs.getObject("id", UUID::class.java),
			fromAccountId = rs.getObject("from_account_id", UUID::class.java),
			toAccountId = rs.getObject("to_account_id", UUID::class.java),
			amount = rs.getLong("amount"),
			currency = rs.getString("currency"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			reversesTransferId = rs.getObject("reverses_transfer_id", UUID::class.java),
		)
	}

	fun insertTransfer(
		id: UUID,
		fromAccountId: UUID,
		toAccountId: UUID,
		amount: Long,
		currency: String,
		idempotencyKey: String,
		reversesTransferId: UUID?,
	): TransferResponse =
		jdbcTemplate.query(
			"""
			INSERT INTO transfers (id, from_account_id, to_account_id, amount, currency, idempotency_key, reverses_transfer_id)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			RETURNING id, from_account_id, to_account_id, amount, currency, created_at, reverses_transfer_id
			""",
			rowMapper,
			id, fromAccountId, toAccountId, amount, currency, idempotencyKey, reversesTransferId,
		).single()

	fun findByIdempotencyKey(idempotencyKey: String): TransferResponse? =
		jdbcTemplate.query(
			"""
			SELECT id, from_account_id, to_account_id, amount, currency, created_at, reverses_transfer_id
			FROM transfers
			WHERE idempotency_key = ?
			""",
			rowMapper,
			idempotencyKey,
		).firstOrNull()

	fun findById(id: UUID): TransferResponse? =
		jdbcTemplate.query(
			"""
			SELECT id, from_account_id, to_account_id, amount, currency, created_at, reverses_transfer_id
			FROM transfers
			WHERE id = ?
			""",
			rowMapper,
			id,
		).firstOrNull()

	fun sumOfReversals(originalId: UUID): Long =
		jdbcTemplate.queryForObject(
			"SELECT COALESCE(SUM(amount), 0) FROM transfers WHERE reverses_transfer_id = ?",
			Long::class.java,
			originalId,
		) ?: 0L

	fun insertEntry(transferId: UUID, accountId: UUID, amount: Long) {
		jdbcTemplate.update(
			"INSERT INTO entries (transfer_id, account_id, amount) VALUES (?, ?, ?)",
			transferId, accountId, amount,
		)
	}
}
