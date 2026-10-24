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
		)
	}

	fun insertTransfer(
		id: UUID,
		fromAccountId: UUID,
		toAccountId: UUID,
		amount: Long,
		currency: String,
	): TransferResponse =
		jdbcTemplate.query(
			"""
			INSERT INTO transfers (id, from_account_id, to_account_id, amount, currency)
			VALUES (?, ?, ?, ?, ?)
			RETURNING id, from_account_id, to_account_id, amount, currency, created_at
			""",
			rowMapper,
			id, fromAccountId, toAccountId, amount, currency,
		).single()

	fun insertEntry(transferId: UUID, accountId: UUID, amount: Long) {
		jdbcTemplate.update(
			"INSERT INTO entries (transfer_id, account_id, amount) VALUES (?, ?, ?)",
			transferId, accountId, amount,
		)
	}
}
