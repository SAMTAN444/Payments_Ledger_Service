package dev.samueltan.ledger.account

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AccountRepository(
	private val jdbcTemplate: JdbcTemplate,
) {
	private val rowMapper = RowMapper<AccountResponse> { rs, _ ->
		AccountResponse(
			id = rs.getObject("id", UUID::class.java),
			ownerName = rs.getString("owner_name"),
			currency = rs.getString("currency"),
			kind = rs.getString("kind"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
		)
	}

	fun insert(id: UUID, ownerName: String, currency: String, kind: String): AccountResponse =
		jdbcTemplate.query(
			"""
			INSERT INTO accounts (id, owner_name, currency, kind)
			VALUES (?, ?, ?, ?)
			RETURNING id, owner_name, currency, kind, created_at
			""",
			rowMapper,
			id, ownerName, currency, kind,
		).single()

	fun findById(id: UUID): AccountResponse? =
		jdbcTemplate.query(
			"""
			SELECT id, owner_name, currency, kind, created_at
			FROM accounts
			WHERE id = ?
			""",
			rowMapper,
			id,
		).firstOrNull()

	// lock both accounts in id order, so two opposite transfers can't deadlock
	fun findTwoForUpdate(firstId: UUID, secondId: UUID): List<AccountResponse> =
		jdbcTemplate.query(
			"""
			SELECT id, owner_name, currency, kind, created_at
			FROM accounts
			WHERE id IN (?, ?)
			ORDER BY id
			FOR UPDATE
			""",
			rowMapper,
			firstId, secondId,
		)

	// balance = sum of the account's entries, or 0 if it has none
	fun balanceOf(accountId: UUID): Long =
		jdbcTemplate.queryForObject(
			"SELECT COALESCE(SUM(amount), 0) FROM entries WHERE account_id = ?",
			Long::class.java,
			accountId,
		) ?: 0L
}
