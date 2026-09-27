# AgriOptima — Project Status

_Last updated: 2026-09-27 (Milestone 8)_

## Current milestone

**Milestone 8 — React dashboard: COMPLETE (awaiting review)**
Next: to be decided after review (weather / LLM / what-if are not started).
Working tree is intentionally **uncommitted** (Milestones 3–8) pending review.

## Milestones

| # | Milestone | Status |
|---|---|---|
| 1 | Requirements + architecture | ✅ Done (approved) |
| 2 | Database + backend foundation | ✅ Done (approved) |
| 3 | Data acquisition + ML pipeline | ✅ Done (approved) |
| 4 | Recommendation engine | ✅ Done (approved) |
| 5 | Optimization engine | ✅ Done (approved) |
| 6 | ML API | ✅ Done (approved) |
| 7 | Spring Boot integration (end-to-end recommendation) | ✅ Done (approved) |
| 8 | React dashboard | ✅ Done — awaiting review |
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

## Milestone 3 — what was implemented

Details: [docs/DATA_CARD.md](docs/DATA_CARD.md) (dataset investigation + cleaning),
[docs/MODEL_CARD.md](docs/MODEL_CARD.md) (model, metrics, limitations),
`ml-service/reports/evaluation_report.md` (generated by the training script).

### Dataset decision
**Real public data, no synthetic data.** CIMMYT CSISA *Landscape Diagnostic Survey* 2018: wheat
(hdl:11529/10548507, v2.0, 7,648 plots, Bihar + eastern UP) and rice (hdl:11529/10548656, v3.0, 8,355 plots,
8 states). They were chosen because they record, per real field, **which fertilizer products were applied and how much**
(basal + up to 3 top-dressings) together with yield. Rejected: Kaggle "Crop Yield in Indian States" (its
`Fertilizer` column was measured to be Area × one national constant per year, the same for every crop and state), Kaggle
Crop Recommendation / Fertilizer Prediction / Agriculture Crop Yield (no yield or no quantity), TAMASA Nigeria
omission trials (file missing on the server, HTTP 404), ICRISAT DLD (district totals across all crops).
Data papers are CC BY 4.0. Raw/processed files are downloaded by script, not committed.

### Pipeline (`ml-service/`)
```
training/download_data.py   Dataverse download + MD5 check          -> data/raw/lds/
training/lds.py             validate_raw -> tidy (derive features) -> clean (logged rules)
training/prepare_data.py    runs the above                          -> data/processed/lds_wheat_rice_2018.csv,
                                                                       reports/data_validation.json
training/train.py           district-grouped split, grouped 5-fold CV + grid search, 4 models, selection,
                            permutation importance, partial dependence -> artifacts/, reports/
app/features.py             feature contract shared by training and inference (nutrient conversion,
                            sowing_day, category normalisation, preprocessor, leakage exclusion list)
app/schemas.py              YieldScenario (validated inference input), YieldPrediction
app/model_store.py          loads artifact (sha256 + feature-list check), clips to supported range, flags extrapolation
app/main.py                 FastAPI: GET /health, GET /model/info   (/predict-yield -> M6, /optimize -> M5/M6)
```
Cleaning: 16,003 → **15,399 rows** (wheat 7,622, rice 7,777, 88 districts); every rule logged with counts.

### Features (all known before fertilizer is applied)
Scenario: `n_kg_ha`, `p2o5_kg_ha`, `k2o_kg_ha` (derived from products × grade ÷ plot ha), `zn_applied`.
Context: `sowing_day`, `irrigation_available`, `fym_applied`, `crop`, `state`, `soil_texture`, `variety_type`,
`previous_crop`. Target: `yield_t_ha` (farmer-reported). Excluded as leakage: harvest date, crop duration,
production, crop-cut yield, lodging, drought/flood/pest severity, irrigation count, weeding counts, prices,
fertilizer delay, interview date, enumerator device (enforced in code and tests).

### Actual results (district-grouped; `python -m training.train`)
| Model | CV RMSE | CV R² | Test MAE | Test RMSE | Test R² |
|---|---|---|---|---|---|
| baseline (crop mean) | — | — | 1.024 | 1.4067 | 0.299 |
| Linear Regression | 1.0303 ± 0.041 | 0.4649 | 0.8221 | 1.0611 | 0.6011 |
| Decision Tree | 1.0859 ± 0.046 | 0.403 | 0.8808 | 1.1387 | 0.5407 |
| Random Forest | 1.0312 ± 0.0296 | 0.4627 | 0.8344 | 1.0807 | 0.5862 |
| **XGBoost (selected, lowest CV RMSE)** | **1.022 ± 0.0276** | 0.4717 | 0.851 | 1.1045 | 0.5678 |

Selected model per crop (test): wheat MAE 0.5502 / RMSE 0.7133 / R² 0.2951; rice 1.1669 / 1.4024 / 0.3536.
Naive random-split CV would have reported R² 0.6323 (spatial leakage), vs 0.4717 grouped.

### Model artifact
`ml-service/artifacts/yield_model.joblib` + `metadata.json`, version **`yield-lds2018-xgboost-20260927`**,
sha256 `17284e7a…dc41059`, 192 KB. Reproducible: two full pipeline runs gave identical metrics and a
byte-identical artifact.

### Tests — actual results
`scripts/ml.sh test` / `.\scripts\ml.ps1 test` → **46 passed** (pytest 9.1.1, 2026-09-27).

| File | Tests | Covers |
|---|---|---|
| `test_features.py` | 20 | nutrient conversion, complex grades, sowing_day (incl. January wheat), state/previous-crop normalisation, unseen categories, leakage guard on feature names |
| `test_lds.py` | 12 | per-plot kg → nutrient kg/ha, grade parse/impute, each cleaning rule, duplicate removal, validation failures, no raw survey column leaks into features |
| `test_model.py` | 14 | committed artifact loads; predictions plausible and batched; inference == training preprocessing; different plans → different yields; clipping + extrapolation flag; determinism; invalid inputs (maize, unknown state, negative dose, extra field) rejected; tampered artifact refused; `/health`, `/model/info`, degraded mode without artifact |

Manual check: `uvicorn app.main:app --port 8001` → `/health` 200 `{"status":"UP","model_loaded":true,...}`,
`/model/info` 200, `/predict-yield` 404 (not built yet, by design).

---

## Milestone 4 — what was implemented

Details, sources, values and worked examples: [docs/RECOMMENDATION_ENGINE.md](docs/RECOMMENDATION_ENGINE.md).

### Deterministic nutrient-requirement engine (`backend/src/main/java/com/agrioptima/engine/`)
Pure Java: no Spring, JPA, ML, optimisation, weather or LLM inside, and no I/O (the "as of" date is an input).
`NutrientRequirementEngine.calculate(RequirementInput) -> NutrientRequirement`:
1. profile (explicit `?profile=`, else rainfed / late-sown / crop default) -> general dose, kg/ha N, P2O5, K2O
2. soil test -> LOW / MEDIUM / HIGH per nutrient -> factor x1.25 / x1.00 / x0.75 (no soil test: ASSUMED_MEDIUM, x1.00 + warning)
3. split schedule -> cumulative share due up to and including the current stage (exact fractions)
4. previous applications in the season window: kg product x grade % ÷ field ha
5. `dueNow = max(0, adjusted x share - applied)` → **`requirementForOptimizer`** (kg/ha and whole-field kg);
   remaining season and excess are also reported
Warnings: no soil test, stale soil test, strongly acidic/alkaline pH, over-application, and missed earlier splits
("window has passed"). Every response carries its assumptions, the knowledge-base version and a disclaimer.

### Knowledge base (versioned, sourced)
`backend/src/main/resources/knowledge/nutrient-kb-v1.json`, **v1.0.0, status PROTOTYPE**. Crops wheat/rice/maize,
16 profiles, 5 split schedules, soil rating limits, pH/OC classes. Every value is labelled `REFERENCED`,
`MAPPING_ASSUMPTION`, `PROTOTYPE_ASSUMPTION` or `DERIVED`, with a source id. Sources were read on 2026-09-27:
ICAR-IIWBR Extension Bulletin 52 (wheat), ICAR-NRRI and ICAR-CRRI pages (rice doses), TNAU Agritech (rice split),
Tamil Nadu Crop Production Guide (maize), Deshmukh et al. 2022 Table 2 citing Muhr et al. 1965 (soil ratings),
and IUPAC atomic weights (P/K oxide factors). The ±25 % soil-test adjustment, season window, stale-test age and
some stage mappings are **prototype assumptions**, labelled as such. Loaded strictly at startup
(`KnowledgeBaseLoader`: unknown keys, splits ≠ 1, bad references → startup fails) and cross-checked against the
Flyway crop/stage codes (`KnowledgeBaseConsistencyCheck`). No DB migration was needed.

### API added
| Method | Path | Notes |
|---|---|---|
| GET | `/api/fields/{fieldId}/nutrient-requirement[?profile=CODE]` | owner-scoped, calculated on request, not persisted |
| GET | `/api/knowledge-base` | the KB in use, with sources and statuses |
| GET, POST | `/api/fields/{fieldId}/applications` | previous usage (kg product per field; stage must belong to field crop) |
| DELETE | `/api/applications/{applicationId}` | owner-scoped (foreign → 404) |

### Tests — actual results
`.\scripts\backend.ps1 build` (2026-09-27): **Tests run: 130, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**
(43 existing + 87 new).

| Class | Tests | Covers |
|---|---|---|
| `NutrientRequirementEngineTest` | 51 | wheat/rice/maize; stages SOWING→JOINTING, CRI catch-up, schedule positions; LOW/MEDIUM/HIGH and mixed soil; every rating boundary (e.g. N 279.99/280/560/560.01); no soil test; oxide equivalents; pH and stale-test warnings; field sizes 0.01–100 ha; applications converted per ha; season window edges and future dates; no sowing date fallback; excess; rainfed, late-sown (25/26 Nov, Jan) and explicit profiles; unknown crop/stage/profile; invalid area, soil, pH, OC, dates, negative nutrients; determinism |
| `KnowledgeBaseLoaderTest` | 19 | shipped KB valid, versioned, values match cited documents, every dose referenced; rejects splits ≠ 1, unknown source/schedule/status, unordered ratings/factors, negative dose, unknown or missing keys; fraction parsing |
| `NutrientUnitsTest` | 9 | P↔P2O5, K↔K2O factors and round trips, product → nutrients, per-ha ↔ field totals, invalid area/grade/quantity |
| `NutrientRequirementIntegrationTest` | 8 | end-to-end via HTTP + H2: KB ↔ DB consistency, basal applied via DAP/urea/MOP → due 40 N, low soil vs none, explicit profile, rice 0.5 ha, 400s (no crop, no stage, bad/unknown profile), 401/404 ownership, applications CRUD + validation, KB endpoint |

Mutation check: changing LOW 1.25 → 1.30 in the KB made 3 tests fail (engine + integration); the file was restored.

### Manual runtime verification (dev profile, 2026-09-27)
Startup log: "Loaded nutrient knowledge base agrioptima-nutrient-kb v1.0.0 (PROTOTYPE)". Six scenarios via the HTTP API
(results in docs/RECOMMENDATION_ENGINE.md §5), e.g. wheat CRI 2 ha, medium soil, basal applied → due 40/0/0 kg/ha
(80 kg N for the field); rice PI 0.5 ha, high soil → 67.5/45/22.5 kg/ha; unknown profile → 400 problem+json.
`/v3/api-docs` lists 17 paths including the 4 new ones. The dev H2 database now contains the demo users/fields
from this run (delete `backend/data/` to reset).

---

## Milestone 5 — what was implemented

Details, formulation, assumption labels and actual results: [docs/OPTIMIZER.md](docs/OPTIMIZER.md).

### Optimizer (`ml-service/app/optimizer.py`)
Pure Python function `optimize(OptimizationRequest) -> OptimizationResult` (pydantic models, CLI
`python -m app.optimizer request.json`). It has no FastAPI route yet (M6), uses no yield model or LLM (enforced by a
test), and never changes the requirement. Decision variables x_i = kg/ha of each available product. Hard
constraints: supply of N, P2O5 and K2O ≥ the M4 requirement, 0 ≤ x_i ≤ cap. Solver: `scipy.optimize.linprog`, HiGHS
dual simplex (`highs-ds`), SciPy 1.18.1.

| Plan | Objective | Tie-break |
|---|---|---|
| LOWEST_COST | min Σ price·x | least total excess |
| MIN_EXCESS | min Σ_j (supplied_j − required_j) | lowest cost |
| BALANCED | min total excess s.t. cost ≤ C_A + β(C_B − C_A), β = 0.5 | lowest cost |

- Ties are broken exactly by restricting stage 2 to the optimal face (complementary slackness with the HiGHS duals).
- Exact feasibility pre-check (x = caps) → `INFEASIBLE` with a per-nutrient reason (no source, caps, or no
  products).
- `NOTHING_REQUIRED` → three empty plans. A derived dominance bound keeps products with no required nutrient at 0.
- Numerics: the LP is solved for requirement + 1e-6 kg/ha, and quantities are rounded to 1 g/ha (nearest if still
  exact, else up). Every plan is re-checked with **no tolerance** before it is returned; a plan that fails raises an
  error and is never returned.
- Output per plan: kg/ha and field kg per product, N/P2O5/K2O supplied and excess (per ha and per field), cost (per
  ha and per field), total mass, feasibility, recomputed objective value and `same_as`. Warnings: binding caps and
  unavoidable excess.

### Spring re-check (`backend/.../engine/plan/FertilizerPlanVerifier.java`)
Pure Java. Recomputes everything from the backend's own catalogue and the plan's quantities. Rejects unknown or
duplicate products, negative/NaN/∞/above-cap quantities, field kg ≠ kg/ha × area, supply < requirement − 1e-9 kg/ha,
claimed totals that differ by more than 1e-9 relative, and a mismatched `feasible` flag. It is not yet called from
any request flow (M7).

### Actual results (V2 catalogue, M4 example requirements)
| Requirement N/P2O5/K2O (kg/ha) | LOWEST_COST | MIN_EXCESS | BALANCED |
|---|---|---|---|
| 40/0/0 (wheat CRI, 2 ha) | Urea 86.957, ₹514.79/ha | same | same |
| 100/75/50 (wheat, low soil) | DAP 54.348 + NPK 192.308 + Urea 154.319, ₹8,034.82/ha | same | same |
| 67.5/45/22.5 (rice PI, 0.5 ha) | DAP 48.914 + NPK 86.539 + Urea 108.787, ₹4,508.94/ha (₹2,254.47 field) | same | same |
| 30/30/30 (maize rainfed) | NPK 115.385 + Urea 40.134, ₹3,629.91/ha | same | same |
| 0/60/40 (wheat, P+K only) | DAP 43.479 + NPK 153.847, ₹5,697.03, **23.21 kg N excess** | MOP 66.667 + SSP 375, ₹6,391.68, 0 excess | MOP 19.754 + NPK 108.262 + SSP 199.076, ₹6,044.37, 10.83 kg N excess |

Rounding leaves < 1 g/ha excess per nutrient. The three strategies differ only where cost and excess actually
conflict (last row).

### Tests — actual results
- `.\scripts\ml.ps1 test` → **96 passed** (46 existing + 50 new in `tests/test_optimizer.py`), all on the real
  HiGHS solver with nothing mocked. Covered: N-only, single nutrient, overlapping DAP/NPK nitrogen, 100/75/50
  cross-checked against HiGHS interior point, the three strategies on the conflict case (plus the BALANCED
  budget/excess guarantee and β = 0 and 1), unavoidable excess, ties, zero requirement, missing nutrient source, no
  fertilizers, default/raised/binding/exact caps, awkward floats, 150 random requirements, cost/excess/mass/field
  arithmetic, violation detection, 0.0001–9,999,999.999 ha scaling, determinism and catalogue order, 15 invalid
  inputs, no ML imports, test catalogue == V2 SQL, and the contract fixture being current.
- `.\scripts\backend.ps1 build` → **Tests run: 150, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**
  (130 existing + `FertilizerPlanVerifierTest` 16 + `FertilizerPlanVerifierContractTest` 4, which re-checks all 27
  plans from the real optimizer in `backend/src/test/resources/optimizer/optimizer-contract.json`).
- Mutation checks (reverted): removing the round-up fallback and the margin → 19 pytest failures; Java tolerance
  1e-3 → 3 failures.
- 1,000 random requests: about 12 ms per request (3 plans each); no quantity ≤ 2 g/ha in 3,000 plans.

---

## Milestone 6 — what was implemented

Contract, units, examples and error table: [docs/ML_API.md](docs/ML_API.md).

### Layers (`ml-service/app/`)
| File | Role |
|---|---|
| `api/routes.py` | 4 thin routes: schema-validated body → one service call → response model; Swagger examples |
| `api/schemas.py` | `HealthResponse`, `ModelInfoResponse`, `PredictYieldRequest/Response`, `OptimizeResponse` (units in descriptions) |
| `services.py` | `ModelState` (loaded once at startup, or the safe reason why not), `health`, `model_info`, `predict_yield` (supported-crop check, clipping warnings), `run_optimizer` (adds `feasible`, `infeasibility_reason`, `units`) |
| `errors.py` | RFC 7807 problem+json (`urn:agrioptima:problem:*`, same shape as the backend); no traces/paths/input echo |
| `config.py` | `MODEL_ARTIFACT_DIR`, `ML_CORS_ALLOWED_ORIGINS` (exact origins, `*` refused, empty = off) |
| `main.py` | `create_app(settings)`: lifespan loads the model, CORS (GET/POST, Content-Type, no credentials), handlers |

Changes to existing M3/M5 code (no retraining, no change to the model or to optimizer maths):
- `model_store.py`: `ModelLoadError.public_reason` (safe client message); missing model file / bad metadata JSON /
  undeserialisable pickle now raise `ModelLoadError` instead of crashing startup; `YieldPrediction.clipped_inputs`
  (requested value, used value, bounds) next to the existing `clipped_features`; `feature_version` fingerprint;
  `YieldModel.info()` moved to `services.model_info`.
- `optimizer.py`: field descriptions (units) on the request/result models only.
- `features.py` bug fix: the canonical `previous_crop` value `PULSE` sent by a client was grouped as OTHER. The training
  table is unchanged: it was re-derived in memory with md5 `b41094e4…`, identical to the one in the artifact metadata.

### Endpoints
| Method | Path | Result |
|---|---|---|
| GET | `/health` | 200; `UP`, or `DEGRADED` when the model is not loaded (optimizer still UP); no inference |
| GET | `/model/info[?crop=]` | 200 metadata (version, type, crops, feature version, datasets, split/CV, metrics, sha256); 503 without model |
| POST | `/predict-yield` | batch of 1–50 scenarios → t/ha + clipping details; 422 `unsupported-crop` (e.g. MAIZE); 503 without model |
| POST | `/optimize` | M5 result + `feasible`/`infeasibility_reason`/`units`; INFEASIBLE is **200** with reasons; 500 `optimization-failed` |

### Tests — actual results (2026-09-27)
- `.\scripts\ml.ps1 test` → **188 passed** (test_api 90, test_features 24, test_lds 12, test_model 12,
  test_optimizer 50). Two old endpoint tests moved from test_model to test_api; 4 PULSE cases added to test_features.
  One warning: Starlette's deprecation notice for `httpx` in `TestClient` (a library notice, not a test problem).
- `tests/test_api.py` uses the real artifact and the real HiGHS solver. Only the solver-failure and unexpected-exception
  paths are simulated. It covers: health UP / no inference / artifact removed after startup; missing, invalid-JSON,
  tampered-sha, corrupt-pickle and feature-drift artifacts (DEGRADED, 503, safe reason, no paths); model loaded exactly
  once; `/model/info` values, checksum = file sha256, crop support, no paths; batch prediction, order,
  API == `YieldModel.predict` == raw pipeline, normalisation, determinism; N and sowing-date clipping; maize and mixed
  batches; 10 invalid fields, missing fields, batch size, extra fields, NaN/±∞, malformed JSON, no input echo;
  `/optimize` == `optimize()` for the M4 example and all 10 contract cases, field totals, INFEASIBLE (no source, caps,
  no products), NOTHING_REQUIRED, 22 malformed optimizer requests, NaN price; clean 500s; 404/405; CORS
  allow/deny/disabled/parsing; OpenAPI paths, error responses, units, and every Swagger example executed.
- `scripts/backend.sh test` → **Tests run: 150, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** (unchanged; the
  optimizer contract fixture is still current).

### Manual runtime verification (uvicorn, 2026-09-27)
`uvicorn app.main:app --port 8001`: startup log "loaded yield model yield-lds2018-xgboost-20260927 (sha256 17284e7a…)".
`/health` UP; `/model/info?crop=maize` → `supported:false`; wheat 120/60/40 → 3.107 t/ha; N 400 → 3.257 t/ha with
`extrapolation:true` and N used = 217.38; MAIZE → 422 problem+json; rice PI 0.5 ha → ₹4,508.94/ha, ₹2,254.47 per field
(same as M5); no K source → 200 INFEASIBLE with reason; bad requirement → 422 with `errors`; CORS preflight from
localhost:5173 allowed; `/docs` 200. With `MODEL_ARTIFACT_DIR` set to an empty directory (port 8002): `/health` DEGRADED
"model metadata not found", `/predict-yield` 503, `/optimize` still OPTIMAL; the full path appears only in the server log.

---

## Milestone 7 — what was implemented

Pipeline, model inputs, scoring, example response and errors: [docs/RECOMMENDATION_FLOW.md](docs/RECOMMENDATION_FLOW.md).

### Endpoint and flow
`POST /api/fields/{fieldId}/recommendations[?profile=]` → 201 with the full recommendation (stored). Flow:
ownership (404) → M4 `NutrientRequirementService` → ML `/optimize` → `FertilizerPlanVerifier` (failing plans dropped;
none left → 502) → ML `/predict-yield` (one season-total scenario per plan) → `PlanScoringService` → persist.
Also added: `GET /api/fields/{fieldId}/recommendations` (history) and `GET /api/recommendations/{id}` (stored run).

### New code (backend)
| Where | What |
|---|---|
| `ml/MlServiceClient`, `MlContracts`, `MlServiceException`, `MlServiceProperties` | RestClient on `java.net.http` (HTTP/1.1), `ML_SERVICE_BASE_URL` (default `http://localhost:8001`), connect 2 s / read 15 s; maps refused/timeout/error/invalid bodies to typed failures; validates response shape |
| `service/recommendation/RecommendationService` | orchestration; no DB transaction during HTTP calls; checks the optimizer echoed our requirement and area |
| `service/recommendation/YieldFeatureMapper` | backend fields → model features, each labelled FIELD / MAPPED / DERIVED / PROTOTYPE_DEFAULT / NOT_RECORDED |
| `service/recommendation/PlanScoringService` | yield × price − cost − ₹20/kg × excess; cost-and-excess mode without yields; deterministic tie-break |
| `RecommendationProperties` + `application.yml` `app.recommendation` | MSP 2025-26 crop prices, excess penalty, 500 kg/ha cap (all prototype, reported in responses) |
| `controller/RecommendationController`, `dto/recommendation/*` | endpoint + frontend-oriented response |
| `db/migration/V3__recommendations.sql`, `Recommendation`, `RecommendationPlan`, repository | persistence (key columns + full response JSON; plan summary rows) |
| `GlobalExceptionHandler` | 503 `ml-service-unavailable`, 504 `ml-service-timeout`, 502 `ml-service-error` / `ml-service-invalid-response` |
| `scripts/demo_recommendation.py` | end-to-end demo against the running services |

Missing model inputs are handled explicitly:
- **state:** taken from the farm location (e.g. "Patna, Bihar"). If none is found, or several, there is no prediction.
- **sowing date:** required; if missing, there is no prediction.
- **soil type → texture:** a documented mapping.
- **irrigation not set:** assumed irrigated (labelled).
- **variety / previous crop not set:** imputed by the model pipeline.
- **FYM:** assumed none (labelled).
- **Zn:** derived as false.
- **Maize:** plans are returned, but with no yield. The ML service's 422 `unsupported-crop` is translated into
  `yieldPrediction.available=false`.

### Bugs found and fixed during this milestone
- Jackson's snake_case strategy mapped `predictedYieldTHa` to `predicted_yield_tha`, so every yield silently
  deserialised as **0.0** (seen in the first real end-to-end run). Fixed with an explicit `@JsonProperty`; yields are
  now `Double` and the client rejects a missing or negative yield.
- `HttpURLConnection` (SimpleClientHttpRequestFactory) re-sent the POST after a read timeout, so a timeout surfaced as
  an unreadable response (502) and the ML service received the request twice. Replaced by `JdkClientHttpRequestFactory`.
- The `@Lob` column failed Hibernate validation on H2 (`LONGTEXT` is VARCHAR there) and was replaced by a plain String
  with `columnDefinition = "LONGTEXT"`. `createdAt` is returned rounded to microseconds, as stored.

### Tests — actual results (2026-09-27)
- `scripts/backend.sh test` → **Tests run: 192, Failures: 0, Errors: 0, Skipped: 1 — BUILD SUCCESS** (150 existing +
  42 new; the skipped one is the opt-in live test).
- With the real ML service running: `ML_LIVE_BASE_URL=http://localhost:8001 scripts/backend.sh test` →
  **Tests run: 192, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**.
- `.\scripts\ml.ps1 test` → **188 passed** (ML code unchanged in M7).

| Class | Tests | Covers |
|---|---|---|
| `RecommendationIntegrationTest` | 14 | wheat end to end (request to optimizer and yield model, mapped features, season totals, persistence, history, GET = POST); rice; infeasible (no prediction call, stored); ML 500 / bad JSON / missing fields / wrong echo / timeout → 502/504, not stored; yield failure → cost-and-excess mode; maize; unknown state; ownership (404, 401, ML never called); no soil test; no crop / bad profile / unknown field; each plan gets its own prediction (predictions returned out of order); deterministic selection that matches the documented formula; tampered plans dropped by the verifier and all bad → 502; every plan meets the requirement |
| `RecommendationMlUnavailableTest` | 1 | connection refused → 503 with no URL, nothing stored |
| `RecommendationLiveMlTest` | 1 | real FastAPI: wheat P+K case, real yields, M5 plan costs (opt-in via `ML_LIVE_BASE_URL`) |
| `PlanScoringServiceTest` | 5 | formula, yield vs cost trade-off, fallback mode, ties independent of input order, candidates untouched |
| `YieldFeatureMapperTest` | 21 | state detection (incl. ambiguous, substring, misspelling alias), texture mapping, input sources, rainfed / no sowing date |

`RecommendationIntegrationTest` uses `FakeMlService` (JDK HttpServer), which replays the real optimizer output from
the M5 contract fixture and returns a deterministic yield function.

### Manual end-to-end run (real services, 2026-09-27)
FastAPI on 8001 + Spring Boot (dev profile, Flyway "Successfully applied 1 migration ... v3") on 8080, then
`scripts/demo_recommendation.py`:
- **Wheat CRI 2 ha:** 40/0/0 → Urea 86.957 kg/ha, ₹514.79/ha, 3.25 t/ha.
- **Rice PI 0.5 ha:** DAP + NPK + Urea, ₹4,508.94/ha (₹2,254.47 for the field), 3.967 t/ha.
- **Wheat P+K only:** three different plans (3.317 / 3.234 / 3.292 t/ha); LOWEST_COST selected, score ₹74,275.98/ha.
- **Maize:** plans returned, "does not support MAIZE", cost-and-excess mode.
- Results were identical after a backend restart.
- **ML service stopped:** 503 `ml-service-unavailable`, history empty.
- **Bad token:** 401.

---

## Milestone 8 — what was implemented

React 19 + Vite 8 + Tailwind CSS 4 + Axios + React Router 7 + Recharts 3 + lucide-react (icons), in `frontend/`.
No state library: an `AuthContext` and a small `useAsync` hook. The UI only displays backend values; it calculates no
requirement, quantity, yield or score (the only logic is grouping plans that the backend marked `sameAs`).

| Route | Page | APIs |
|---|---|---|
| `/login` | sign-in (validation, wrong-credential message, session-expired notice) | `POST /api/auth/login` |
| `/` | dashboard: farms, fields (crop · stage, area, latest soil test, last recommendation), recent recommendations | farms, fields, soil-records, recommendations history |
| `/farms/:farmId` | farm → field selection | `GET /api/farms`, `/api/farms/{id}/fields` |
| `/fields/:fieldId` | field workspace: crop & stage (edit), soil (view / add test + soil type), due-now preview, profile select, **Generate Recommendation** | field GET/PUT, soil-records GET/POST, nutrient-requirement, knowledge-base, crops/stages, `POST .../recommendations` |
| `/recommendations/:id` | showpiece: field summary, nutrient requirement, plan comparison table (sameAs collapsed, selected highlighted), 3 small bar charts (cost / predicted yield / excess), recommended plan, warnings & assumptions (+ default model inputs, scoring, versions, disclaimer); infeasible and nothing-due states | `GET /api/recommendations/{id}` (or the POST response) |
| `/history` | stored runs, filter by field, open one | `GET /api/fields/{id}/recommendations` |

Auth: JWT + user + expiry in `localStorage`; Axios request interceptor adds `Authorization: Bearer`; expired tokens are
dropped on load; any 401 from a protected call signs out with a notice. RFC 7807 problems are mapped to readable
messages (503/504/502 ML problems, 400 validation with per-field errors, network failure).

Demo data: `scripts/demo_recommendation.py --seed-demo-user` creates/uses **demo@agrioptima.local / demo-pass-123**
(Demo farm, Patna, Bihar: Wheat - CRI, Rice - PI, Wheat - P+K only, Maize - sowing) and runs one real recommendation
per field; it does nothing if that account already has farms.

### Tests — actual results (2026-09-27)
`cd frontend; npm test` → **4 files, 23 tests passed** (Vitest 5, jsdom, Testing Library). `npm run build` succeeds.

| File | Tests | Covers |
|---|---|---|
| `client.test.js` | 6 | bearer header, expired session dropped, 401 → sign-out handler (not for login), ML 503 + validation mapping, network error |
| `auth.test.jsx` | 5 | redirect to login, successful login (loading state, token stored, dashboard), wrong password, client validation, sign out |
| `flow.test.jsx` | 7 | farm → field selection, field page (soil, stage, profile), generate (loading, profile passed, response rendered without refetch), ML unavailable, no crop/stage, no soil test, soil range validation, history → open |
| `recommendation.test.jsx` | 5 | real captured responses: three distinct plans + selected highlight, sameAs collapse, selected plan represents its group, all warnings/assumptions, infeasible |

### Manual run against the real services (2026-09-27)
FastAPI 8001 + Spring Boot 8080 (dev) + Vite 5173, driven in headless Chrome: login (wrong password → message; demo
account → dashboard), Farms → Wheat - P+K only → Generate → recommendation #101 with LC ₹5,697 / 3.317 t/ha, ME
₹6,392 / 3.234, BAL ₹6,044 / 3.292 (identical to M7); soil test saved (pH 12 rejected client-side), rice stage
changed → due now updated → generated #102; maize run shows "Not available" yield and cost-and-excess wording;
ML service stopped → "Optimisation service unavailable" message; no browser console errors. Rice field restored to
its seeded state afterwards.

## How to run the backend

```powershell
# from repo root (PowerShell)
.\scripts\backend.ps1 run      # API on http://localhost:8080 (dev profile, H2 file DB in backend/data/)
.\scripts\backend.ps1 test     # 192 tests (1 skipped unless ML_LIVE_BASE_URL is set)
.\scripts\backend.ps1 build    # clean package -> backend/target/backend-0.2.0.jar
```
```bash
# Git Bash / Linux / macOS
scripts/backend.sh run | test | build
```
Swagger UI: http://localhost:8080/swagger-ui.html · H2 console (dev): http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:file:./data/agrioptima-dev`, user `sa`, empty password). Reset dev data: delete `backend/data/`.

### ML pipeline / service

```powershell
.\scripts\ml.ps1 pipeline   # download (MD5-checked) -> prepare -> train (~2.5 min)
.\scripts\ml.ps1 test       # 188 tests
.\scripts\ml.ps1 serve      # http://localhost:8001/docs  (/health, /model/info, /predict-yield, /optimize)
.\scripts\ml.ps1 optimize   # optimizer CLI on an example request (or: optimize path\to\request.json)
```
(`scripts/ml.sh ...` in Git Bash / Linux / macOS.) After an intentional optimizer change, regenerate the contract
fixture: `cd ml-service; .venv\Scripts\python.exe -m tests.optimizer_contract`. Uses `ml-service/.venv` (`pip install -r requirements.txt`).

MySQL profile (for Docker Compose later): `SPRING_PROFILES_ACTIVE=mysql` with `MYSQL_HOST`, `MYSQL_PORT`,
`MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` and **`JWT_SECRET` (required)**.

---

## Known issues / limitations

### React dashboard (Milestone 8)
- No create/delete screens for farms and fields (seed via the demo script or Swagger); soil tests can only be added.
- JWT in `localStorage` (prototype; XSS-readable). No refresh tokens; a 12 h token ends the session.
- Dashboard/history load one soil + one history request per field (fine for a handful of fields).
- The loading state lists the real pipeline steps without per-step progress (the backend call is one request).
- Infeasible rendering is tested with a modified fixture; the demo data has no real infeasible case.
- Bundle is ~720 kB (Recharts); not code-split yet.

### End-to-end recommendation (Milestone 7)
- **The model can reward surplus N.** In the wheat P+K demo, LOWEST_COST wins partly because its 23 kg/ha of surplus
  N raises the predicted yield (observational model). The ₹20/kg excess penalty is small next to that; the weight is
  a product decision (one config value).
- **Yield predictions need the state in the farm location text and a sowing date.** There is no dedicated state
  field.
- **Prototype scoring inputs:** crop prices (MSP 2025-26) and the penalty are prototype values. There is no
  sustainability or composite score, baseline "current practice" plan, schedule or weather yet (later milestones).
- **Season-total scenario assumes later splits happen as planned:** it uses the requirement DTO values, which are
  rounded to 0.01 kg/ha.
- **Plan line items are stored only inside the JSON.** Recommendations are immutable snapshots (no update/delete
  endpoint).
- **One synchronous request with two ML calls:** there is no retry or circuit breaker. A slow ML service holds the
  request up to 15 s per call.
- **The optimizer often returns three identical plans** (`sameAs`). The UI should collapse them.

### ML service API (Milestone 6)
- No authentication: the service must be reachable only from the backend (Docker network, M12). CORS is not a
  security boundary.
- Validation errors use 422 (FastAPI convention), while the backend uses 400. The M7 client maps ML 4xx/5xx
  responses to backend 502/503/504 problems, and yield failures to a fallback.
- The backend still stores no `state`, `variety_type` or FYM/Zn flags. M7 maps or labels them (see the M7 section).
- `/health` returns 200 even when DEGRADED (liveness). A readiness check must read `status`/`model.loaded`.
- Single process, and the model is loaded per worker. There is no rate limiting or request-size limit beyond the
  schema caps (50 scenarios / 50 fertilizers).
- `REDGRAM` (2 rice rows) was grouped as OTHER in training; inference keeps that grouping to match the model.

### Optimizer (Milestone 5)
- Linear, single-application model: 100 % of the labelled nutrient is counted, with no losses or efficiency
  differences, no S/Zn credit and no rounding to whole bags. Excess counts kg N, P2O5 and K2O equally
  (**prototype assumption**).
- The default cap of 500 kg/ha per product and β = 0.5 are **prototype tuning parameters**, not agronomic values.
- Prices are the indicative V2 values; LOWEST_COST and the BALANCED budget depend on them directly.
- Architecture §7's weighted-λ plan C was replaced by the budget (ε-constraint) form (reason in docs/OPTIMIZER.md §3).
- `FertilizerPlanVerifier` is not yet called from a request flow (M7).
- The contract fixture is exact solver output. A SciPy/HiGHS upgrade may change last-digit values; the pytest then
  asks for a review and regeneration.

### Recommendation engine (Milestone 4)
- Blanket recommendations with a ±25 % soil-test adjustment (**prototype assumption**, no primary source found);
  not STCR or site-specific nutrient management.
- Agro-climatic zone / ecosystem is not inferred: farms have no state/zone field, so the default profile may be
  wrong for a location (Bihar/UP wheat should use `IRRIGATED_TIMELY_SOWN_NWPZ_NEPZ`, 150 kg N). The caller must
  pass `?profile=` until location → zone mapping exists.
- Maize values are from the Tamil Nadu guide (ICAR-IIMR unreachable); the TN hybrid dose (250 kg N) is high.
- Missed earlier splits are still counted as due (with a warning); P/K catch-up after sowing may be ineffective.
- No credit for residual nutrients, FYM, legumes; no S/Zn/micronutrients; pH and OC only produce warnings.
- The requirement is calculated on request and not persisted (persistence comes with Milestone 7).
- `FieldRequest` does not restrict sowing dates, so a future sowing date simply shifts the season window.

### ML (Milestone 3)
- Yield data is **observational and farmer-reported** (r = 0.56 vs crop cuts). The model ranks plans by
  association; it is not a causal dose-response model and cannot penalise over-application.
- Modest accuracy on unseen districts: within-crop test R² 0.30 (wheat) / 0.35 (rice).
- **Maize has no training data**: the ML service rejects it; M7 returns maize plans without a yield (cost-and-excess ranking).
- Model inputs the backend does not store yet: `state` (farms have lat/lon + free-text location only),
  `variety_type`, FYM/Zn flags. Mapping `fields.soil_type` → LIGHT/MEDIUM/HEAVY and `irrigation_type` →
  `irrigation_available` is needed. M7: mapped/labelled per docs/RECOMMENDATION_FLOW.md §2 (no new columns).
- 602 rice / 137 wheat rows used an unmapped "other" fertilizer (ignored → nutrient totals possibly understated).
- The ML code and artifacts are not committed yet (metadata records `ml_code_uncommitted: true` against d4623a7).

### Backend (Milestone 2)

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
| 15 | Yield model trained on real LDS survey data; test/CV split **by district** | Farms in one village share conditions; a row split inflated CV R² from 0.47 to 0.63. |
| 16 | Model selection by mean grouped-CV RMSE, fixed before looking at the test set | LR scored better on test (0.60 vs 0.57) but switching would be test-set selection. |
| 17 | Whole sklearn Pipeline saved as one artifact; loader checks sha256 + feature list | Training/inference preprocessing cannot drift; a swapped or stale artifact fails fast. |
| 18 | Inference clips numeric inputs to the per-crop 0.5–99.5th pct training range and flags `extrapolation` | Tree models are flat/unreliable outside the data; the caller sees when that happens. |
| 19 | Knowledge base is a versioned JSON resource, not DB tables | Values + source + status travel together, are diffable in review, validated strictly at startup; no user editing needed yet. |
| 20 | Engine is a pure class (`com.agrioptima.engine`) wired by `KnowledgeBaseConfig` | Deterministic, unit-testable without Spring/DB; the service only loads data. |
| 21 | Split fractions stored as exact rationals ("1/3") | "Sums to 1" is checked exactly; no floating-point tolerance hides a wrong split. |
| 22 | Requirement = cumulative share due to current stage − already applied (catch-up included, with warning) | Simple, explainable; late-application judgement is left to the farmer/agronomist. |
| 23 | Soil N/P/K stored and rated on the elemental basis; requirements on the oxide basis | Matches Indian soil-test reports and fertilizer grades; conversions derived from atomic weights. |
| 24 | Plan C (BALANCED) = min excess s.t. cost ≤ C_A + β(C_B − C_A), not a weighted λ objective | The weighted form (λ_e ₹20, λ_s ₹0.5) equalled plan A on all 5 M4 examples; the budget form gives a real intermediate plan with a provable ≥ 50 % excess cut for ≤ 50 % of the premium. |
| 25 | Lexicographic tie-breaks via the optimal face (duals), not an objective cut | A `primary ≤ opt + tol` row produced spurious 0.001 kg/ha products. |
| 26 | Quantities in whole g/ha; requirement checked with zero tolerance in Python, 1e-9 kg/ha in Java | "Never under-supply because of rounding" is enforced by a check; the Java tolerance only covers summation order. |
| 27 | The Python ↔ Java contract is a generated fixture of real solver output, checked from both sides | The Java verifier is tested against real plans, and a pytest fails when the optimizer output changes. |

## Dataset decisions

| Date | Decision |
|---|---|
| 2026-09-27 | Train on **real** CIMMYT CSISA LDS 2018 wheat + rice surveys (applied fertilizer per product + quantity + yield). No synthetic data. See docs/DATA_CARD.md. |
| 2026-09-27 | Kaggle "Crop Yield in Indian States" rejected: its `Fertilizer` column is measured to be Area × a yearly national constant. |
| 2026-09-27 | Soil-test N/P/K/pH and weather are **not** yield-model inputs (no usable dataset has them with applied fertilizer); they drive the M4 requirement engine and M10 weather rules. |

## ML model version

`yield-lds2018-xgboost-20260927` (XGBoost regressor, t/ha, wheat + rice). Metrics above; full detail in
`ml-service/artifacts/metadata.json` and docs/MODEL_CARD.md.

## Important assumptions

- Yield model: fertilizer grades per FCO 1985; default complex grade for 175 rows with a missing grade;
  plot acres × 0.40469 = ha; free-text "other" fertilizers ignored (see docs/DATA_CARD.md §5).
- Recommendation engine: see the status column in docs/RECOMMENDATION_ENGINE.md §3. `PROTOTYPE_ASSUMPTION`
  items: ±25 % soil adjustment, soil P/K read as elemental, season window (sowing − 30 d; 150 d fallback),
  stale-test age (1095 d), TNAU rice split applied to all rice profiles, no carry-over credit.
- Soil N/P/K are entered as plant-available kg/ha on the elemental basis (fixed in Milestone 4). Validation bounds (N ≤ 2000, P ≤ 1000, K ≤ 3000, pH 3–11,
  OC ≤ 20 %, moisture ≤ 100 %) are **data-entry sanity limits, not agronomic thresholds**.
- Fertilizer prices in V2 are indicative INR/kg prototype values, not an official price list.
- Optimizer: the per-product cap (500 kg/ha default), the BALANCED budget share β = 0.5, equal weighting of
  N/P2O5/K2O excess and 100 % nutrient availability are **prototype assumptions** (docs/OPTIMIZER.md §4–5), not
  scientific constants.
- Sustainability score (to be defined later) is a prototype metric, not an official standard.
- All recommendations are estimates for a hackathon prototype and must be validated with soil tests and
  qualified local agronomic guidance.
