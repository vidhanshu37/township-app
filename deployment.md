# Deployment and local run guide

GitHub: https://github.com/<your-username>/<repo-name>

The app has three parts: a PostgreSQL 16 database, a Spring Boot 4 backend (Java 17), and a React web app that can be installed as a PWA.

## Environment variables

Only names are listed here. Copy `.env.example` to `.env` and fill in the values. Never commit `.env`.

| Name | What it is |
|---|---|
| `DB_URL` | JDBC URL of the database. For local runs: `jdbc:postgresql://localhost:5433/township` |
| `DB_USER` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret that signs login tokens. At least 32 characters |
| `PAYMENT_WEBHOOK_SECRET` | Secret that signs the payment webhook |
| `APP_DEMO_MODE` | `true` shows the OTP on screen and in the API response. Use `false` for anything real |
| `AI_API_KEY` | Optional. Key for the AI that sorts complaints (Groq, free at console.groq.com). Empty means keyword rules only; the app works fully either way |
| `AI_MODEL` | Optional. Default `llama-3.3-70b-versatile`. `AI_BASE_URL` (default Groq) and `AI_TIMEOUT_MS` (default 5000) also exist |

With Docker (option A) `DB_URL` is set by `docker-compose.full.yml`; the other five values come from `.env`.

## Option A: everything in Docker (needs only Docker)

```
cp .env.example .env        # then edit the values
docker compose -f docker-compose.full.yml up --build
```

Open http://localhost:8080. The first build takes a few minutes. Database tables and demo data are created automatically by Flyway when the backend starts.

Stop with `Ctrl+C`. To delete the data as well: `docker compose -f docker-compose.full.yml down -v`.

## Option B: run each part on your machine

Needs Docker (for the database), Java 17 and Node 18 or newer.

1. Database:
   ```
   docker compose up -d db
   ```
   This uses the `docker-compose.yml` in the repo root (PostgreSQL on host port 5433).
2. Backend (new terminal):
   ```
   cd backend
   ./mvnw spring-boot:run
   ```
   Windows: `mvnw.cmd spring-boot:run`. It listens on http://localhost:8085 and reads `.env` from the repo root.
3. Web app (new terminal):
   ```
   cd frontend
   npm install
   npm run dev
   ```
   Open http://localhost:5173. In development, `/api` is forwarded to the backend, so no CORS setup is needed.

## Demo logins

Sign in with a phone number. The OTP appears on screen when `APP_DEMO_MODE=true`.

| Phone | Role | Flat |
|---|---|---|
| 9000000001 | Resident (Neeraj) | T07-1203 |
| 9000000002 | Resident (Swati) | T12-0408 |
| 9000000003 | Resident (Aamir) | T21-1601 |
| 9000000010 | Help desk | none |
| 9000000020 | Committee | none |

## A quick tour (about 5 minutes)

1. Sign in as `9000000002`. See the dues, pay part of them (a demo payment), open the receipt, check the ledger, and report a problem such as "Lift is stuck between floors".
2. Sign in as `9000000010` (help desk). On Statements, upload `docs/sample-statement.csv`. Some rows match automatically and some wait for review. Confirm one, reject one. Open Complaints and move the lift complaint to in progress. Under Billing, generate next month's bills.
3. Sign in as `9000000020` (committee). The overview shows collection, overdue flats, bank matching and complaints.

## Tests

```
cd backend
./mvnw test
```

## Notes

- The payment gateway is simulated. No real money moves.
- The OTP is never sent by SMS. It is shown on screen only in demo mode.
- Passwords and secrets are not in the repository. Only their names are.