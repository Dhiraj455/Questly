# Questly Backend

Ktor + PostgreSQL service for Questly v3 (accounts & backend). Contract: `../docs/api/openapi.yaml`.
This is a standalone Gradle build; run it with the repo's root Gradle wrapper via `-p backend`.

## Run locally

1. Start PostgreSQL:

   ```bash
   docker compose -f backend/docker-compose.yml up -d
   ```

2. Run the server (from the repo root):

   ```bash
   ./gradlew.bat -p backend run
   ```

3. Check it's up:

   ```bash
   curl http://localhost:8080/health
   ```

   → `{"status":"ok","service":"questly-backend","version":"0.1.0"}`

## Test

```bash
./gradlew.bat -p backend test
```

The health-check test runs without a database. Configuration (DB URL/user/password, and later the
Brevo and Google credentials) comes from environment variables — see `.env.example`.

## Migrations

Flyway runs `src/main/resources/db/migration/V*.sql` on startup. Migrations are additive; never edit
one that has been applied.
