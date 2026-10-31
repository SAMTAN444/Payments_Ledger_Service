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

	private val entryRowMapper = RowMapper<EntryResponse> { rs, _ ->
		EntryResponse(
			entryId = rs.getLong("id"),
			transferId = rs.getObject("transfer_id", UUID::class.java),
			amount = rs.getLong("amount"),
			counterpartyAccountId = rs.getObject("counterparty_account_id", UUID::class.java),
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

	fun availableBalanceOf(accountId: UUID): Long =
		jdbcTemplate.queryForObject(
			"""
			SELECT
				(SELECT COALESCE(SUM(amount), 0) FROM entries WHERE account_id = ?)
				- (SELECT COALESCE(SUM(amount), 0) FROM holds WHERE from_account_id = ? AND status = 'ACTIVE')
			""",
			Long::class.java,
			accountId, accountId,
		) ?: 0L

	fun findEntries(accountId: UUID, before: Long?, limit: Int): List<EntryResponse> =
		jdbcTemplate.query(
			"""
			SELECT e.id,
				e.transfer_id,
				e.amount,
				CASE
					WHEN t.from_account_id = e.account_id THEN t.to_account_id
					ELSE t.from_account_id
				END AS counterparty_account_id,
				t.created_at
			FROM entries e
			JOIN transfers t ON t.id = e.transfer_id
			WHERE e.account_id = ?
				AND e.id < ?
			ORDER BY e.id DESC
			LIMIT ?
			""",
			entryRowMapper,
			accountId, before ?: Long.MAX_VALUE, limit,
		)
}
