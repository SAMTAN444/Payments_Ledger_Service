# ledger-service

A payments ledger service (learning project). Kotlin, Spring Boot, JDBC, PostgreSQL, Flyway.

Requires JDK 21 and Docker.

## Start Postgres

```sh
docker compose up -d     # Postgres 16 on localhost:5434 (db/user/password: ledger)
docker compose down      # stop it (add -v to also delete the data)
```

It uses host port 5434 rather than the default 5432, so it won't clash with a locally
installed Postgres.

## Run the app

```sh
./gradlew bootRun
curl localhost:8080/actuator/health
```

Flyway applies the migrations in `src/main/resources/db/migration` on startup.

## Run the tests

```sh
./gradlew test      # tests only
./gradlew build     # compile + tests + jar (what CI runs)
```

Integration tests start their own throwaway Postgres via Testcontainers. Docker must be
running, but `docker compose` does not need to be.
