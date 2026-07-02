# First Round Integration Smoke Report

Date: 2026-06-28
Branch: `codex/integration`

## Scope

This round covers first-round smoke testing and contract consistency checks on the current `codex/integration` code.

Out of scope for this round:

- New business capabilities
- OpenAPI master contract expansion
- Database baseline changes
- Governance or role document changes
- Unapproved API boundary changes

## Checks Run

| Check | Command / path | Result |
|---|---|---|
| Branch check | `git status --short --branch` | PASS: `codex/integration...origin/codex/integration` |
| Scaffold invariant | `node tests/integration/verify-scaffold.mjs` | PASS |
| Ownership check | `node tests/integration/check-ownership.mjs` | PASS, 468 repository files |
| Secret scan | `node tests/integration/scan-secrets.mjs` | PASS |
| OpenAPI validation | `npm run validate` in `tests/contract` | PASS with existing 8 Redocly `no-ambiguous-paths` warnings |
| Backend tests | `mvn test` in `backend` | PASS earlier in this round: 360 tests, 0 failures/errors/skipped |
| Frontend tests | `npm run test` in `frontend` | PASS: 2 files, 7 tests |
| Frontend type check | `npm run type-check` in `frontend` | PASS |
| Frontend build | `npm run build` in `frontend` | PASS with existing Rolldown pure annotation and chunk size warnings |
| Compose config | `docker compose config --quiet` | PASS; local Docker config permission warning still appears |
| Docker build/start | `BACKEND_PORT=18080 docker compose up --build -d backend frontend` | PASS |
| Backend health | `GET http://localhost:18080/actuator/health` | PASS: `UP` |
| Frontend page | `GET http://localhost:8088/` | PASS: HTTP 200 and Vue app shell present |
| Frontend-to-backend auth proxy | `POST http://localhost:8088/api/auth/login` | PASS: `SUCCESS`, token returned |
| Real patient flow through frontend proxy | register -> login -> `GET /api/patients/me` through `http://localhost:8088/api` | PASS: same patient id returned |
| Reproducible real API smoke script | `node tests/integration/smoke-real-api.mjs` | PASS: backend `UP`, frontend `OK`, patient register/login/me all `SUCCESS` |
| Browser page load | Playwright CLI `open http://localhost:8088` + `snapshot` | PASS: page title and login form rendered; slider login was left for manual Edge verification |

## Fixes Applied In This Round

| Issue | Fix | Owner |
|---|---|---|
| Drug contraindication route differed from OpenAPI: backend used `/api/drugs/{code}/contraindications`, contract uses `/api/drugs/contraindications/{drugCode}` | Backend route changed to contract path | Integration AI / Backend AI |
| `TriageAnalyzeRequest.patientId` used `@NotBlank` on `Long` | Changed to `@NotNull` | Integration AI / Backend AI |
| Frontend auth, patient, department, appointment, and triage API clients still used local mock paths | Switched supported patient-side clients to real `/api` calls and added client tests | Integration AI / Frontend AI |
| Patient appointment and triage views directly consumed local mock departments/schedules | Switched department loading to `/api/departments`; appointment display now uses backend appointment response fallback | Integration AI / Frontend AI |
| Compose backend did not pass required runtime secrets/config consistently | Added backend env wiring for `JWT_SECRET`, `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD`, and `CORS_ALLOWED_ORIGINS`; README updated | Integration AI |
| Docker backend failed on first real MySQL startup because `AdminInitializer` saved `user_account.created_at = null` | Admin initializer now fetches/creates `ADMIN` role and sets audit timestamps before saving user/role | Integration AI / Backend AI |
| Docker backend logged file appender errors because `/app/logs` did not exist for non-root runtime user | Backend Dockerfile now creates `logs` and grants ownership before switching to `cloudbrain` user | Integration AI |
| Real API smoke flow was only documented manually | Added `tests/integration/smoke-real-api.mjs` and documented it in `tests/integration/README.md` | Integration AI |

## Real Docker Integration Result

Current running services after the fix:

```text
cloud-brain-mysql-1      healthy   0.0.0.0:3306->3306/tcp
cloud-brain-backend-1    healthy   0.0.0.0:18080->8080/tcp
cloud-brain-frontend-1   running   0.0.0.0:8088->80/tcp
```

Notes:

- Host port `8080` is occupied on this machine. Compose already supports `BACKEND_PORT`, so this run used `BACKEND_PORT=18080`.
- Frontend `/api/` is proxied by Nginx to `backend:8080` inside the Compose network.
- Initial admin login succeeds, but protected APIs are blocked until the required first password change. This is expected backend behavior.
- A generated patient smoke account was registered through `http://localhost:8088/api/patients/register`, then logged in through the same frontend proxy, and `GET /api/patients/me` returned the same patient id.
- Browser automation opened `http://localhost:8088` and confirmed the rendered login form. Login slider automation was intentionally stopped after manual Edge testing started.

## Remaining Issues

| Priority | Issue | Impact | Suggested owner | Suggested action |
|---|---|---|---|---|
| P1 | OpenAPI does not cover several existing backend controllers, including examination, medical record, prescription, device, statistics, audit, and AI diagnosis APIs | Contract tests and frontend typing cannot cover these modules | Integration lead with Backend AI / Frontend AI / AI capability AI | Freeze task cards or approve contract expansion before wiring more clients |
| P1 | Doctor-side current identity, doctor queue, and diagnosis timeline contracts are not frozen enough for full real frontend wiring | Doctor-side pages still need partial mock fallback | Integration lead with Backend AI / Frontend AI | Confirm whether to add `/api/doctors/me`, dashboard aggregation, or timeline contracts |
| P2 | OpenAPI has 8 Redocly `no-ambiguous-paths` warnings | Not blocking validation, but may affect generated clients or gateway routing rules | Integration lead / Architecture AI / Backend AI | Keep as known warning or approve route/lint-rule adjustment |
| P2 | `TriageRecordResponse` has `mappedDepartmentId` and `aiDepartmentCode`, but not `mappedDepartmentName` | Patient triage history cannot reliably display department display name without extra lookup/fallback | Backend AI / Frontend AI | Approve response field addition or frontend dictionary lookup strategy |
| P3 | Docker CLI still warns that `C:\Users\cdk\.docker\config.json` cannot be read | Does not block public-image Compose use; may affect private registry auth | Local environment / Integration AI | Fix local Docker config permissions if private registry access is needed |
| P3 | Frontend build has third-party `@vueuse/core` pure annotation warnings and a chunk size warning | Build passes; later performance optimization may be needed | Frontend AI | Handle during frontend performance/build tuning |
