# CLAUDE.md

Payments ledger service: Kotlin, Spring Boot 4, JdbcTemplate, PostgreSQL, Flyway. This is a
learning project — the owner writes the business logic themselves.

## Commands

- `docker compose up -d` — start local Postgres 16 (localhost:5434, ledger/ledger/ledger)
- `./gradlew bootRun` — run the app (health: `localhost:8080/actuator/health`)
- `./gradlew test` — run tests (needs Docker; Testcontainers starts its own Postgres)
- `./gradlew build` — compile + test + jar; this is what CI runs

## Rules

- No JPA/Hibernate anywhere. Data access uses JdbcTemplate; schema changes go in Flyway
  migrations under `src/main/resources/db/migration`.
- Never implement `AccountController`, `AccountService`, or `AccountRepository`, and never
  write the body of `AccountApiTest`. Those are the owner's to write — leave them as TODO
  stubs. Explaining or reviewing them is fine.
- Integration tests extend `common/PostgresIntegrationTest`.
- Package by feature: `account/`, `common/`, more features later.
