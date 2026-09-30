# MedFlow AI — local setup, step by step

Everything needed to run the backend (and the UI next to it) on this machine, from an
empty database to a signed-in API call. Commands are written for **Windows PowerShell**;
the same commands work in Git Bash with `/` instead of `\`.

Repository layout on this machine:

```
C:\Users\PCP\Downloads\medflow-ai\
├─ medflow-ai\                                  ← backend (this repository)
└─ medflow-ui\Medflow-UI-Repo\medflow-ai\       ← React + Vite frontend
```

---

## 1. Prerequisites

| Tool | Needed | Check with | Installed here |
|---|---|---|---|
| JDK | 21+ | `java -version` | 21.0.7 ✔ |
| Maven | 3.9+ | `mvn -v` | 3.9.11 ✔ |
| PostgreSQL | 14+ | `Get-Service postgresql*` | 17 ✔ (service `postgresql-x64-17`) |
| Node.js | 20+ | `node -v` | 22.16.0 ✔ (only needed for the UI) |

Docker and Redis are **not** required. Caching defaults to in-memory
(`medflow.cache.provider=simple`), so PostgreSQL is the only infrastructure.

---

## 2. Create the database

Click-by-click walkthrough with screenshots of the pgAdmin tree:
**[PGADMIN_SETUP.md](PGADMIN_SETUP.md)**.

The short version — in pgAdmin, connected to the `postgres` database as a superuser
(**Tools → Query Tool**, paste, **F5**):

```sql
CREATE DATABASE medflow ENCODING 'UTF8' TEMPLATE template0;
```

That is all. The application connects as the `postgres` superuser by default, which is
the fastest way to get running locally.

Same thing from the command line:

```powershell
$env:PGPASSWORD = "newpassword"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d postgres `
  -f "C:\Users\PCP\Downloads\medflow-ai\medflow-ai\db\setup\00_create_database.sql"
```

**Leave the database empty.** Flyway creates all 24 tables *and* seeds a demo hospital
the first time the application starts — you never write DDL by hand.

> Prefer a dedicated, non-superuser account? Run
> [`db/setup/01_create_role_and_database.sql`](db/setup/01_create_role_and_database.sql)
> and [`02_grants.sql`](db/setup/02_grants.sql), then start the app with
> `$env:DB_USERNAME = "medflow"; $env:DB_PASSWORD = "medflow"`.

---

## 3. Tell the app your database password

The repository never contains credentials. The application defaults to
`postgres` / `postgres`; give it yours in whichever way you prefer:

**Option A — a local config file (recommended).** Spring Boot automatically loads
`./config/application.yml` on top of the packaged configuration, and `config/` is
git-ignored:

```powershell
cd C:\Users\PCP\Downloads\medflow-ai\medflow-ai
Copy-Item -Recurse config.example config     # then edit config\application.yml
```

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/medflow
    username: postgres
    password: your-postgres-password
```

`mvn spring-boot:run` picks it up with no extra flags.

**Option B — environment variables**, handy for CI and containers:

```powershell
$env:DB_USERNAME = "postgres"; $env:DB_PASSWORD = "your-postgres-password"
```

### Everything you can override

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/medflow` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Credentials — set yours (see above) |
| `SERVER_PORT` | `8080` | HTTP port |
| `JWT_SECRET` | dev-only value | HS256 signing key, **≥ 32 characters**; override outside local dev |
| `JWT_TTL` | `PT8H` | Access-token lifetime (ISO-8601 duration) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173,http://localhost:3000` | Browser origins allowed to call the API |
| `CACHE_PROVIDER` | `simple` | In-memory Caffeine cache (30 s). `redis` switches to Redis |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Only used when `CACHE_PROVIDER=redis` |
| `FLYWAY_LOCATIONS` | `classpath:db/migration,classpath:db/demo` | Drop `,classpath:db/demo` for a clean database with no sample data |
| `LOG_LEVEL` | `INFO` | Log level for `com.medflow` |

---

## 4. Start the backend

```powershell
cd C:\Users\PCP\Downloads\medflow-ai\medflow-ai
mvn spring-boot:run
```

First start takes a little longer while Maven resolves dependencies. Success looks like:

```
Flyway Community Edition ... by Redgate
Successfully validated 15 migrations
Migrating schema "public" to version "1 - hospitals and modules"
Migrating schema "public" to version "2 - rbac"
...
Migrating schema "public" to version "100 - demo workspace"
Tomcat started on port 8080 (http)
Started MedflowApplication in 6.4 seconds
```

### No database yet? Run on the in-memory one

Useful for a first look at the UI before PostgreSQL is set up. The schema, the demo
workspace and every endpoint behave identically — the data just disappears on restart:

```powershell
mvn spring-boot:test-run "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:medflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE --spring.datasource.username=sa --spring.datasource.password= --spring.datasource.driver-class-name=org.h2.Driver"
```

H2 is included at runtime specifically for this local profile; the normal application
configuration still defaults to PostgreSQL.

Package and run a jar instead:

```powershell
mvn clean package
java -jar target\medflow-ai-0.0.1-SNAPSHOT.jar
```

---

## 5. Verify it works

| URL | What |
|---|---|
| <http://localhost:8080/health> | Public liveness check |
| <http://localhost:8080/swagger-ui.html> | Interactive API docs — click **Authorize** to paste a token |
| <http://localhost:8080/api-docs> | Raw OpenAPI document |
| <http://localhost:8080/actuator/health> | Liveness / readiness probes |
| <http://localhost:8080/actuator/prometheus> | Metrics |

### Sign in with the seeded demo workspace

| Account | Email | Password | Role |
|---|---|---|---|
| Administrator | `admin@medflow.local` | `Admin@12345` | ADMIN |
| Doctor | `kabir@medflow.local` | `Doctor@12345` | DOCTOR |
| Receptionist | `reception@medflow.local` | `Doctor@12345` | RECEPTIONIST |
| Lab technician | `lab@medflow.local` | `Doctor@12345` | LAB_TECHNICIAN |

PowerShell:

```powershell
$login = Invoke-RestMethod -Uri http://localhost:8080/api/v1/auth/login -Method Post `
  -ContentType 'application/json' `
  -Body '{"email":"admin@medflow.local","password":"Admin@12345"}'

$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }

# Dashboard KPIs for the signed-in hospital
Invoke-RestMethod -Uri http://localhost:8080/api/v1/analytics/dashboard -Headers $headers | ConvertTo-Json -Depth 5

# Today's schedule
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/appointments?size=10" -Headers $headers | ConvertTo-Json -Depth 5
```

curl / Git Bash:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@medflow.local","password":"Admin@12345"}' | jq -r .data.accessToken)

curl -s http://localhost:8080/api/v1/analytics/dashboard -H "Authorization: Bearer $TOKEN"
```

### Or create your own workspace

`POST /api/v1/auth/register` provisions a **new hospital** plus its first administrator
and returns a token immediately:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/v1/auth/register -Method Post `
  -ContentType 'application/json' `
  -Body '{"fullName":"Ananya Rao","email":"you@yourclinic.com","password":"changeit-123","confirmPassword":"changeit-123","hospitalName":"Your Clinic"}'
```

Every subsequent request is scoped to the hospital baked into that token — one tenant can
never read another's rows.

### Adding more test data

The demo workspace (1 hospital, 6 users, 3 doctors, 5 patients, 9 appointments,
3 prescriptions, 4 lab orders, 6 medications, 4 alerts) is created automatically. To add
more:

**Through the API** — recommended, because it generates codes, hashes passwords, assigns
queue numbers and stamps the tenant for you:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/v1/patients -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Body '{"firstName":"Aarav","lastName":"Menon","gender":"MALE","dateOfBirth":"1988-02-14","bloodGroup":"B+","phone":"+91 90000 11111"}'
```

**In bulk with SQL** — [`db/setup/03_sample_test_data.sql`](db/setup/03_sample_test_data.sql)
adds 6 patients, 18 appointments across the next three days, extra stock and lab orders.
Open it in the pgAdmin Query Tool against the **medflow** database and press F5, or:

```powershell
$env:PGPASSWORD = "newpassword"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d medflow `
  -f "C:\Users\PCP\Downloads\medflow-ai\medflow-ai\db\setup\03_sample_test_data.sql"
```

It is safe to run repeatedly — each run appends a new batch. Dashboard figures are cached
for 30 seconds, so give it a moment (or refresh twice) before the new counts appear.

---

## 6. Run the UI beside it

```powershell
cd C:\Users\PCP\Downloads\medflow-ai\medflow-ui\Medflow-UI-Repo\medflow-ai
npm install
npx vite --host 127.0.0.1 --port 5173
```

Open <http://localhost:5173> and sign in with `admin@medflow.local` / `Admin@12345`.
The UI calls the backend directly — no proxy — and port 5173 is already in the API's
allowed CORS origins.

The API base URL comes from `.env` (copied from `.env.example`):

```
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

Change it if the backend runs on another port or host, then restart `npm run dev` — Vite
only reads env files at start-up.

**What is wired to the API**

| Screen | Data it reads / writes |
|---|---|
| Login / Signup / Forgot password | `POST /auth/login`, `POST /auth/register` (creates a hospital + its admin), `POST /auth/forgot-password` |
| Dashboard | KPI cards, 7-day activity chart, recent appointments, newest patients, unread alerts |
| Doctor Management | Paged, searchable roster with specialty, experience and fee |
| Patient Management | Paged, searchable records with age, blood group and status |
| Appointment Management | Filter by status; **Confirm → Check in → Start → Complete** walk the real workflow |
| Prescription Management | Prescriptions with diagnosis, medicine lines and signature state |
| Laboratory | Test queue with priority; **Start** moves an order into processing |
| Pharmacy | Catalogue with a reorder-list toggle; +10 restock and dispense adjust stock |
| Notifications | Feed with unread filter, mark one / mark all read (also drives the header badge) |
| User Management | Staff directory; admins can activate or deactivate accounts |
| Settings | Hospital profile, editable per-hospital settings, licensed module badges |
| AI Assistant | Live conversation against `POST /assistant/messages` |

Session handling: the JWT is kept in `localStorage`, restored on refresh and validated
against `/auth/me`; any `401` from any call signs you out and returns you to the login
screen. Protected routes redirect anonymous visitors to `/login`, then send them back to
the page they originally wanted.

Storybook (component workshop): `npm run storybook` → <http://localhost:6006>.

Reports & Analytics is still a placeholder screen — the numbers it will use are already
served by `/analytics/*`.

## 7. Everyday commands

```powershell
mvn test                     # full suite: module-boundary check + end-to-end smoke test (H2, no database needed)
mvn clean package            # build the runnable jar
mvn spring-boot:run          # run with live config
mvn -DskipTests clean install
```

Run without the sample workspace (empty database, register your own hospital):

```powershell
$env:FLYWAY_LOCATIONS = "classpath:db/migration"
mvn spring-boot:run
```

Use Redis for the dashboard cache:

```powershell
$env:CACHE_PROVIDER = "redis"
$env:REDIS_HOST = "localhost"
```

---

## 8. API map

All routes are prefixed `/api/v1`. 🔓 = public, 👑 = ADMIN only; everything else needs a
bearer token.

| UI module | Endpoints |
|---|---|
| Auth | 🔓 `POST /auth/register`, 🔓 `POST /auth/login`, 🔓 `POST /auth/forgot-password`, 🔓 `POST /auth/reset-password`, `GET /auth/me` |
| Workspace | `GET /hospital`, 👑 `PUT /hospital`, `GET /hospital/modules` |
| Dashboard | `GET /analytics/dashboard`, `GET /analytics/activity?days=7` |
| Doctor Management | 👑 `POST /doctors`, `GET /doctors`, `GET /doctors/{id}`, 👑 `PUT /doctors/{id}`, 👑 `PATCH /doctors/{id}/status`, `GET|POST /doctors/{id}/availability`, `DELETE /doctors/{id}/availability/{slotId}`, `GET /doctors/{id}/staff`, 👑 `POST /doctors/{id}/staff` |
| Patient Management | `POST /patients`, `GET /patients`, `GET /patients/{id}`, `PUT /patients/{id}`, `GET|POST /patients/{id}/medical-history`, `GET|POST /patients/{id}/reports`, `GET|POST /patients/{id}/accounts` |
| Appointment Management | `POST /appointments`, `GET /appointments`, `GET /appointments/{id}`, `PATCH /appointments/{id}/{confirm｜check-in｜start-consultation｜complete｜cancel｜no-show｜reschedule}` |
| Prescription Management | `POST /prescriptions`, `GET /prescriptions`, `GET /prescriptions/{id}`, `PATCH /prescriptions/{id}/complete`, `PATCH /prescriptions/{id}/cancel` |
| Laboratory | `POST /lab-orders`, `GET /lab-orders`, `GET /lab-orders/{id}`, `PATCH /lab-orders/{id}/{start｜complete｜cancel}` |
| Pharmacy | `POST /pharmacy/medications`, `GET /pharmacy/medications?lowStockOnly=true`, `GET|PUT /pharmacy/medications/{id}`, `PATCH /pharmacy/medications/{id}/stock` |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` |
| AI Assistant | `POST /assistant/messages`, `GET /assistant/messages` |
| User Management | `GET /users`, `GET /users/{id}`, 👑 `POST /users`, `PUT /users/{id}`, 👑 `PATCH /users/{id}/role`, 👑 `PATCH /users/{id}/status`, `GET /access/roles`, `GET /access/user-groups`, 👑 `GET /audit-logs` |
| Settings | `GET /settings`, `GET /settings/{key}`, 👑 `PUT /settings/{key}` |

Every response uses the same envelope:

```json
{
  "success": true,
  "message": "Patients retrieved successfully",
  "data": { "content": [], "page": 0, "size": 20, "totalElements": 5, "totalPages": 1 },
  "errors": [],
  "timestamp": "2026-08-01T10:15:30Z",
  "traceId": "0f0c1e0e-..."
}
```

---

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection to localhost:5432 refused` | Start the `postgresql-x64-17` service. |
| `password authentication failed for user "medflow"` | Role missing or different password — see [PGADMIN_SETUP.md](PGADMIN_SETUP.md) step 2, or `ALTER ROLE medflow WITH PASSWORD 'medflow';` |
| `permission denied for schema public` | Run the grants in step 2 while connected to the `medflow` database. |
| `Unsupported Database: PostgreSQL 17` | `flyway-database-postgresql` missing from the classpath — `mvn clean package` with this repository's `pom.xml`. |
| `Validate failed: Migration checksum mismatch` | A migration changed after it ran. Reset with `db/setup/99_reset_database.sql`, then restart. |
| `Schema-validation: missing table [x]` | The application started against a database Flyway did not migrate (wrong `DB_URL`) — check which database you are pointing at. |
| `NoClassDefFoundError` / `Unable to find a suitable main class` at startup | A stale or half-written `target/` — stop the app, then `mvn clean compile`. |
| 500 on `/patients`, `/doctors`, `/users`, `/pharmacy/medications` | Fixed in this repository (PostgreSQL needs an explicit `CAST(:query AS string)` for a null search parameter). If you see it again, you are running an older build — `mvn clean package`. |
| `Port 8080 was already in use` | `$env:SERVER_PORT = "8081"` before `mvn spring-boot:run`. |
| 401 on every call | Token missing, expired (8 h) or from a previous run with a different `JWT_SECRET` — sign in again. |
| 403 on an admin route | The signed-in account's role is not ADMIN. |
| CORS error in the browser | Add the UI origin to `CORS_ALLOWED_ORIGINS`. |
| Redis connection errors in the log | `CACHE_PROVIDER` is `redis` without a Redis server — unset it to fall back to in-memory caching. |
