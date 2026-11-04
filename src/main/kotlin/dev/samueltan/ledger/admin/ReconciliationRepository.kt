package dev.samueltan.ledger.admin

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

// Each check is one query that returns a "problem" text column, one row per broken rule.
// No rows = healthy.
@Repository
class ReconciliationRepository(
	private val jdbcTemplate: JdbcTemplate,
) {
	private val problemMapper = RowMapper<String> { rs, _ -> rs.getString("problem") }

	// a. All entries in the ledger sum to 0 (double-entry)
	fun ledgerNotBalanced(): List<String> =
		jdbcTemplate.query(
			"""
			SELECT 'The ledger does not sum to zero: total is ' || SUM(amount) AS problem
			FROM entries
			HAVING COALESCE(SUM(amount), 0) <> 0
			""",
			problemMapper,
		)

	// b. Every transfer has exactly 2 entries: -amount on from, +amount on to
	fun badTransferEntries(): List<String> =
		jdbcTemplate.query(
			"""
			SELECT 'Transfer ' || t.id || ' does not have exactly one -' || t.amount
			       || ' entry on its from account and one +' || t.amount
			       || ' entry on its to account (' || COUNT(e.id) || ' entries)' AS problem
			FROM transfers t
			LEFT JOIN entries e ON e.transfer_id = t.id
			GROUP BY t.id, t.amount, t.from_account_id, t.to_account_id
			HAVING COUNT(e.id) <> 2
			    OR COUNT(e.id) FILTER (WHERE e.account_id = t.from_account_id AND e.amount = -t.amount) <> 1
			    OR COUNT(e.id) FILTER (WHERE e.account_id = t.to_account_id AND e.amount = t.amount) <> 1
			""",
			problemMapper,
		)

	// c. No transfer is reversed by more than its amount, and no reversal is itself reversed
	fun badReversals(): List<String> =
		jdbcTemplate.query(
			"""
			SELECT 'Transfer ' || o.id || ' has ' || SUM(r.amount)
			       || ' reversed, more than its amount ' || o.amount AS problem
			FROM transfers o
			JOIN transfers r ON r.reverses_transfer_id = o.id
			GROUP BY o.id, o.amount
			HAVING SUM(r.amount) > o.amount

			UNION ALL

			SELECT 'Transfer ' || r.id || ' reverses ' || o.id
			       || ', which is itself a reversal' AS problem
			FROM transfers r
			JOIN transfers o ON o.id = r.reverses_transfer_id
			WHERE o.reverses_transfer_id IS NOT NULL
			""",
			problemMapper,
		)

	// d. A CAPTURED hold has exactly one capture transfer; ACTIVE and RELEASED holds have none
	fun badCaptures(): List<String> =
		jdbcTemplate.query(
			"""
			SELECT 'Hold ' || h.id || ' is ' || h.status || ' but has '
			       || COUNT(t.id) || ' capture transfer(s)' AS problem
			FROM holds h
			LEFT JOIN transfers t ON t.idempotency_key = 'capture:' || h.id
			GROUP BY h.id, h.status
			HAVING (h.status = 'CAPTURED' AND COUNT(t.id) <> 1)
			    OR (h.status <> 'CAPTURED' AND COUNT(t.id) <> 0)
			""",
			problemMapper,
		)

	// e. No CUSTOMER account has a negative balance or a negative available balance
	fun negativeCustomers(): List<String> =
		jdbcTemplate.query(
			"""
			SELECT 'Customer account ' || id || ' has balance ' || balance
			       || ' and available ' || (balance - held) AS problem
			FROM (
			    SELECT a.id,
			           (SELECT COALESCE(SUM(e.amount), 0) FROM entries e
			            WHERE e.account_id = a.id) AS balance,
			           (SELECT COALESCE(SUM(h.amount), 0) FROM holds h
			            WHERE h.from_account_id = a.id AND h.status = 'ACTIVE') AS held
			    FROM accounts a
			    WHERE a.kind = 'CUSTOMER'
			) per_account
			WHERE balance < 0 OR balance - held < 0
			""",
			problemMapper,
		)
}
