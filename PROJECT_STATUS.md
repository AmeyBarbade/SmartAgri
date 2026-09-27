# AgriOptima — Project Status

_Last updated: 2026-09-27_

## Current milestone

**Milestone 2 — Database + Spring Boot backend foundation: COMPLETE (awaiting approval)**
Next: **Milestone 3 — Data acquisition + ML pipeline** (not started; waiting for explicit approval)

## Milestones

| # | Milestone | Status |
|---|---|---|
| 1 | Requirements + architecture | ✅ Done (approved) |
| 2 | Database + backend foundation | ✅ Done — awaiting review |
| 3 | Data acquisition + ML pipeline | ⏸ Waiting for approval |
| 4 | Recommendation engine | Pending |
| 5 | Optimization engine | Pending |
| 6 | ML API | Pending |
| 7 | Spring Boot integration | Pending |
| 8 | React dashboard | Pending |
| 9 | What-if simulator | Pending |
| 10 | Weather integration | Pending |
| 11 | AI explanation | Pending |
| 12 | Testing + Docker | Pending |
| 13 | Hackathon polish + deployment | Pending |

---

## Milestone 2 — what was implemented

### Stack (as built)
Spring Boot **3.5.16**, Java **21** (Temurin 21.0.12.1 portable, `.tools/`), Maven wrapper (Maven 3.9.16),
Spring Web, Spring Data JPA/Hibernate, Spring Security, jjwt **0.13.0**, Bean Validation, Flyway, H2 2.3,
MySQL Connector/J, springdoc-openapi **2.8.17**. No Lombok (records for DTOs; entities are small).

### Packages (`backend/src/main/java/com/agrioptima/`)
| Package | Contents |
|---|---|
| `controller` | `AuthController`, `FarmController`, `FieldController`, `SoilRecordController`, `ReferenceDataController` |
| `service` | `AuthService`, `FarmService`, `FieldService`, `SoilRecordService`, `ReferenceDataService` |
| `repository` | `User`, `Farm`, `Field`, `SoilRecord`, `Crop`, `CropGrowthStage`, `Fertilizer`, `FertilizerApplication` repositories |
| `entity` | `BaseEntity` (id + audit timestamps), the 8 entities, enums `Role`, `IrrigationType`, `Season` |
| `dto` | `auth/`, `farm/`, `field/`, `soil/`, `reference/` request/response records |
| `security` | `JwtService`, `JwtAuthenticationFilter`, `JwtProperties`, `UserPrincipal`, 401/403 problem handlers |
| `config` | `SecurityConfig` (chain, BCrypt, CORS), `OpenApiConfig`, `AppConfig` (JPA auditing, `Clock`) |
| `exception` | `GlobalExceptionHandler` (RFC 7807), `ResourceNotFoundException`, `ConflictException`, `InvalidRequestException` |

Resources: `application.yml`, `application-dev.yml` (H2 file DB), `application-mysql.yml`,
`db/migration/V1__init_schema.sql`, `db/migration/V2__reference_data.sql`; tests use `application-test.yml`
(in-memory H2).

### Database (Flyway; Hibernate `ddl-auto=validate`)
- **V1** — 8 tables: `users`, `farms`, `fields`, `crops`, `crop_growth_stages`, `soil_records`, `fertilizers`,
  `fertilizer_applications`. Named PKs/FKs/unique/check constraints, indexes on every FK used for lookup
  (`farms.owner_id`, `fields.farm_id`, `soil_records(field_id, sample_date)`,
  `fertilizer_applications(field_id, applied_on)`), `created_at`/`updated_at` on every table.
  `ON DELETE CASCADE` along user → farm → field → soil records / applications.
- **V2** — reference data: 3 crops (Rice, Wheat, Maize) with 16 ordered growth stages (names/order only);
  5 fertilizers (Urea 46-0-0, DAP 18-46-0, MOP 0-0-60, NPK 10-26-26, SSP 0-16-0) with grades per the
  Fertiliser (Control) Order 1985 and **indicative, editable** INR/kg prices (documented in the migration).
- No nutrient requirements / split percentages / stage timings were added (deferred to Milestone 4).

### Endpoints
| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| GET | `/api/auth/me` | JWT |
| GET, POST | `/api/farms` | JWT |
| GET, PUT, DELETE | `/api/farms/{farmId}` | JWT, owner |
| GET, POST | `/api/farms/{farmId}/fields` | JWT, owner |
| GET, PUT, DELETE | `/api/fields/{fieldId}` | JWT, owner |
| GET, POST | `/api/fields/{fieldId}/soil-records` | JWT, owner |
| GET | `/api/fields/{fieldId}/soil-records/latest` | JWT, owner |
| GET, DELETE | `/api/soil-records/{recordId}` | JWT, owner |
| GET | `/api/crops`, `/api/crops/{cropId}/stages`, `/api/fertilizers` | JWT |
| GET | `/swagger-ui.html`, `/v3/api-docs` | public |
| GET | `/h2-console` | public, **dev profile only** |

### Tests — actual results

Command: `.\scripts\backend.ps1 test` (or `scripts/backend.sh test`) → `mvnw test` on JDK 21.
Final run (`mvnw clean package`, 2026-09-27): **Tests run: 43, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**.

| Class | Type | Tests | Covers |
|---|---|---|---|
| `JwtServiceTest` | unit | 6 | round trip, expiry (fixed clocks), foreign key, tampered payload, `alg:none`/garbage, weak secret fails fast |
| `AuthServiceTest` | Mockito | 4 | BCrypt hash stored (never plaintext), email normalisation, duplicate → 409 without save, wrong password vs unknown email identical |
| `FarmServiceTest` | Mockito | 3 | lookups always owner-scoped, foreign update/delete never persist, owner assignment + input cleaning |
| `FieldServiceTest` | Mockito | 3 | no field in a foreign farm, stage must belong to crop, unknown crop |
| `AuthIntegrationTest` | MockMvc + H2 | 8 | register/login/me, hash in DB, 409, validation errors, malformed JSON, 401 without / with tampered token |
| `OwnershipIntegrationTest` | MockMvc + H2 | 5 | user B cannot read/update/delete/attach to user A's farm, field, soil records; lists are per-user; foreign = nonexistent (404) |
| `FarmFieldSoilIntegrationTest` | MockMvc + H2 | 9 | CRUD, crop/stage consistency, soil history ordering + latest, soil validation bounds, cascade delete, bad path param |
| `SchemaAndReferenceDataIntegrationTest` | MockMvc + H2 | 5 | Flyway V1+V2 applied, fertilizer grades, crop stages, reference data needs auth, OpenAPI doc + bearer scheme |

### Manual runtime verification (actually performed, 2026-09-27)
Started with `scripts/backend.sh run` (dev profile) on `.tools/jdk-21.0.12.1+1/bin/java.exe`:
- Log: profile `dev` active; Flyway "Successfully applied 2 migrations … now at version v2" on
  `jdbc:h2:file:./data/agrioptima-dev`; Tomcat on 8080; started in ~6.8 s.
- curl: register → 201 + JWT (HS384); wrong password → 401; `/me` without token 401, bad token 401,
  valid token 200; farm → field (WHEAT / CRI) → two soil records; history newest-first; latest correct;
  pH 15 → 400 problem+json with `errors.ph`; second user got 404 on the first user's farm, field, soil history
  and delete, and an empty farm list; first user's farm still 200 afterwards.
- `/swagger-ui.html` → 200, `/v3/api-docs` → 200 with 13 paths.

### Bug found and fixed during verification
Soil `sampleDate` values were stored one day early (2026-08-20 → 2026-08-19) in tests. Root cause:
`TimeZone.setDefault(UTC)` at runtime after the JDBC layer had initialised in IST (plus
`hibernate.jdbc.time_zone`). Fix: removed both; UTC is now set only at JVM start via
`-Duser.timezone=UTC` (spring-boot:run `jvmArguments`, surefire `argLine`). Must also be set in the Docker
image (Milestone 12).

---

## How to run the backend

```powershell
# from repo root (PowerShell)
.\scripts\backend.ps1 run      # API on http://localhost:8080 (dev profile, H2 file DB in backend/data/)
.\scripts\backend.ps1 test     # 43 tests
.\scripts\backend.ps1 build    # clean package -> backend/target/backend-0.2.0.jar
```
```bash
# Git Bash / Linux / macOS
scripts/backend.sh run | test | build
```
Swagger UI: http://localhost:8080/swagger-ui.html · H2 console (dev): http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:file:./data/agrioptima-dev`, user `sa`, empty password). Reset dev data: delete `backend/data/`.

MySQL profile (for Docker Compose later): `SPRING_PROFILES_ACTIVE=mysql` with `MYSQL_HOST`, `MYSQL_PORT`,
`MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` and **`JWT_SECRET` (required)**.

---

## Known issues / limitations

- **MySQL profile not executed** — no MySQL/Docker on this machine. The schema uses only syntax common to
  MySQL 8.0.16+ and H2 MySQL mode, and enum columns are pinned to VARCHAR for Hibernate validation, but the
  first real MySQL run happens in Milestone 12.
- System Java is 1.8; always use the scripts (they set `JAVA_HOME` to `.tools/jdk-21*`).
- A stray `.tools/jdk21.zip` is being written by a `curl` process not started by this session
  (started 15:03:53, flags `--retry-all-errors -C -`). It was left untouched; it is git-ignored and unused.
- The dev JWT secret is committed in `application-dev.yml` and clearly marked dev-only; the `mysql` profile has
  no default and fails fast without `JWT_SECRET`.
- No refresh tokens / logout / rate limiting on login (acceptable for the prototype; tokens expire in 12 h).
- `fertilizer_applications` table + entity + repository exist, but its API is deferred to the recommendation
  milestone (previous-usage input), per Milestone 2 scope.
- `FarmResponse.fieldCount` is computed with one count query per farm (fine at prototype scale).
- Integration tests share one in-memory DB and isolate by unique users rather than by rollback.

## Architecture decisions (ADR log)

| # | Decision | Reason |
|---|---|---|
| 1 | Optimizer (SciPy LP) lives in the FastAPI service next to the yield model | Stack mandates SciPy; all numerical code in one tested service. Spring re-verifies constraints. |
| 2 | Nutrient-requirement engine is deterministic Java in Spring Boot | Transparent, JUnit-testable, owns the hard constraints the ML/optimizer must respect. |
| 3 | Optimization is a linear program (`linprog`, HiGHS), plans from 3 weight profiles | Exact, fast, explainable; no black-box heuristics. |
| 4 | `OptimizationRun` + `YieldPrediction` folded into `recommendation_plans` | One row per candidate plan carries its optimizer output and prediction; fewer joins, same info. |
| 5 | Weather: Open-Meteo | Free, no API key → no credential risk during the demo. |
| 6 | Explanation is a separate endpoint with a template fallback | Recommendation never blocked by LLM latency/outage. |
| 7 | Flyway for schema migrations; Hibernate `validate` only | Reproducible schema with explicit constraints; entity/schema drift fails at startup. |
| 8 | Nutrient units: kg/ha of N, P2O5, K2O | Matches fertilizer-grade labelling convention. |
| 9 | Spring Boot 3.5.16 pinned manually | start.spring.io now only offers 4.x; brief requires 3.x. |
| 10 | Foreign resources return **404**, not 403 | Ownership checks are inside the repository query; IDs of other users' data cannot be probed. |
| 11 | JWT subject = user id; user re-loaded from DB on every request | Deleted users lose access immediately; role changes apply without re-login. |
| 12 | `fields.growth_stage_id` FK instead of a stage code string | Referential integrity; service also enforces stage ∈ crop. |
| 13 | No Lombok | Records cover DTOs; 8 small entities don't justify an annotation processor. |
| 14 | JVM time zone set at start (`-Duser.timezone=UTC`), never at runtime | See bug fix above. |

## Dataset decisions

_Not yet made (Milestone 3)._ Requirement: yield target + applied-fertilizer features.
If no adequate public dataset exists, a clearly labelled synthetic dataset will be generated.

## ML model version

_None yet._

## Important assumptions

- Soil N/P/K are entered as plant-available kg/ha. Validation bounds (N ≤ 2000, P ≤ 1000, K ≤ 3000, pH 3–11,
  OC ≤ 20 %, moisture ≤ 100 %) are **data-entry sanity limits, not agronomic thresholds**.
- Fertilizer prices in V2 are indicative INR/kg prototype values, not an official price list.
- Optimizer penalty weights (λ) are tuning weights in ₹/kg, not scientific constants.
- Sustainability score (to be defined later) is a prototype metric, not an official standard.
- All recommendations are estimates for a hackathon prototype and must be validated with soil tests and
  qualified local agronomic guidance.
