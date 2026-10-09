# JobForge

AI-powered job marketplace and professional community. Java 21 + Spring Boot (Spring JDBC, Flyway), PostgreSQL, Redis, Kafka,
Mailpit, Next.js + TypeScript + Tailwind + TanStack Query. Everything runs locally with free, open-source components.

Source of truth (highest precedence first): `docs/DATABASE_SCHEMA.md`, `docs/API_CONTRACT.md`, `docs/ARCHITECTURE.md`,
`docs/MASTER_SPEC.md`, `CLAUDE.md`. Current state and open work: `docs/status/HANDOFF.md`.

## Run locally

```bash
bash infrastructure/scripts/gen-secrets.sh        # creates .env from .env.example with random local secrets
docker compose up -d                              # postgres, redis, kafka (+ topics), mailpit
cd backend && mvn spring-boot:run                 # http://localhost:8080  (Flyway migrates a fresh database)
cd frontend && pnpm install && pnpm dev           # http://localhost:3000  (proxies /api/v1 to the backend)
cd ai-service && mvn spring-boot:run              # http://localhost:8090  (optional, LocalTemplateProvider)
```

Or run the applications in containers too: `docker compose --profile app up -d --build`.

Set `SEED_DEV_DATA=true` and `DEV_SEED_PASSWORD` (min 12 chars) in `.env` for a seeded database:
`admin@jobforge.local`, `recruiter@jobforge.local` (approved, owns a verified company), `seeker@jobforge.local`, 60 published jobs.
Mail is captured by Mailpit at http://localhost:8025.

## Recruiter onboarding flow

1. Register as RECRUITER and verify the e-mail (Mailpit).
2. `/recruiter/company` - create the company (you become OWNER); add teammates by e-mail.
3. An ADMIN approves the recruiter and verifies the company at `/admin/approvals`.
4. Draft, edit and publish jobs (`/recruiter/jobs/new`). Publishing requires an approved recruiter and a verified company.

## Tests

```bash
cd backend  && mvn clean verify                   # unit, ArchUnit, Testcontainers ITs (Docker required)
cd frontend && pnpm typecheck && pnpm lint && pnpm test && pnpm build
```
