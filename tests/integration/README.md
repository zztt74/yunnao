# Integration Tests

This directory is maintained by the integration role.

Static checks:

```powershell
node tests/integration/verify-scaffold.mjs
node tests/integration/check-ownership.mjs
node tests/integration/scan-secrets.mjs
node tests/integration/check-collaboration-docs.mjs
```

Real Docker smoke check:

```powershell
$env:BACKEND_PORT = '18080'
docker compose up -d mysql backend frontend
node tests/integration/smoke-real-api.mjs
```

Defaults used by `smoke-real-api.mjs`:

```text
BACKEND_BASE_URL=http://localhost:18080
FRONTEND_BASE_URL=http://localhost:8088
SMOKE_TIMEOUT_MS=10000
```

The real smoke script verifies backend health, the frontend app shell, and a
frontend-proxied patient register -> login -> `/api/patients/me` flow. It
prints only result codes and the generated patient id; it does not print tokens
or passwords.

Local clinic seed:

```powershell
node tests/integration/seed-real-clinic-data.mjs
```

The seed script creates or reuses local-only integration data for the real
appointment flow:

- doctor accounts `doctor_internal_seed` and `doctor_emergency_seed`
- doctor password `DoctorSeed9!2026`
- patient account `patient_seed`
- patient password `PatientSeed9!2026`
- future schedules for the seeded doctors
- one patient appointment for the internal medicine doctor

If the initial admin account still requires a first password change, the script
changes the local Docker admin password to `AdminSeed9!2026` so admin-only APIs
can be used. This does not modify the Flyway baseline.

AI provider mode:

```powershell
$env:BACKEND_PORT = '18080'
docker compose up -d mysql backend frontend
```

Current default is backend `AI_MODE=MOCK`. To use a real OpenAI-compatible HTTP
provider locally, set these values in `.env` and recreate the backend container:

```text
AI_MODE=HTTP
AI_API_URL=https://<provider-host>/v1/chat/completions
AI_API_KEY=<local-secret>
AI_MODEL=<provider-model>
```

Do not commit `.env`. The backend passes the key only through the Authorization
header and does not persist it in AI invocation records.

Full real clinic flow:

```powershell
$env:BACKEND_PORT = '18080'
node tests/integration/e2e-real-clinic-flow.mjs
```

The flow script creates a fresh patient and appointment, then verifies:

- AI triage
- patient appointment
- doctor pending queue
- encounter start
- doctor patient detail access
- final doctor diagnosis
- examination order, device usage, result entry, review
- medical record AI generation with manual fallback
- prescription creation, AI review, confirmation
- encounter completion
- patient record/examination/prescription/encounter visibility
- admin dashboard and audit visibility
- permission denials for patient/admin, doctor/admin, anonymous access

Real AI provider smoke:

```powershell
$env:BACKEND_PORT = '18080'
node tests/integration/smoke-ai-provider.mjs
```

This script calls the configured HTTP provider through the backend and prints
only capability status summaries. It does not print the API key.

AI provider fault injection:

```powershell
$env:BACKEND_PORT = '18080'
node tests/integration/ai-provider-fault-injection.mjs
```

This script starts a local fake OpenAI-compatible provider, recreates the
backend container with a local-only dummy key, verifies non-JSON response,
invalid-schema response, and timeout degradation, then recreates the backend
again with the original Compose environment.
