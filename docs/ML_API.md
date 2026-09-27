# AgriOptima ML service — HTTP API (Milestone 6)

Internal FastAPI service called by the Spring backend (server to server). It exposes the Milestone 3 yield model and
the Milestone 5 optimizer. It has no users, no persistence and no business workflow. Interactive docs:
`http://localhost:8001/docs` (Swagger UI) and `/openapi.json`.

## 1. Architecture

```
app/api/routes.py    thin routes: validate via schema -> call one service function -> return its result
app/api/schemas.py   HTTP request/response contracts (units in every field description)
app/services.py      use cases: ModelState (model loaded at startup or why not), health, model_info,
                     predict_yield (supported-crop check, warnings), run_optimizer (feasible flag, reason, units)
app/errors.py        RFC 7807 problem+json handlers (same shape as the backend), ApiError types
app/config.py        settings from environment (artifact dir, CORS origins)
app/main.py          create_app(): lifespan loads the model once, CORS, error handlers, router
      |
      v
app/model_store.py   YieldModel.load (sha256 + feature-contract check) / predict (clip + flag)   [Milestone 3]
app/schemas.py       YieldScenario -> to_feature_record (training preprocessing, app/features.py) [Milestone 3]
app/optimizer.py     optimize(OptimizationRequest) -> OptimizationResult (SciPy/HiGHS LP)         [Milestone 5]
```

The routes contain no business logic. The services contain no model or optimizer mathematics: a prediction is exactly
`YieldModel.predict`, and an optimization result is exactly `optimizer.optimize` plus three convenience fields.
Tests assert both.

## 2. Units

| Quantity | Unit |
|---|---|
| Predicted yield | t/ha |
| Nutrient requirement, supply, excess (`*_kg_ha`) | kg nutrient/ha, N and oxide basis P2O5, K2O |
| Fertilizer quantities (`items[].kg_ha`, `total_mass_kg_ha`) | kg product/ha |
| `*_field_kg` | kg for the whole field (per-ha value × `area_ha`) |
| `cost_per_ha` / `field_cost` | INR/ha / INR for the whole field |
| `price_per_kg` (request) | INR per kg of product |
| `sowing_day` (clipping details) | days after 1 Oct (wheat) / 1 May (rice), derived from `sowing_date` |

## 3. Endpoints

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/health` | 200 always while running; `status` UP or DEGRADED | — |
| GET | `/model/info[?crop=X]` | 200 | 503 model not loaded |
| POST | `/predict-yield` | 200 | 400 malformed JSON, 422 validation / unsupported crop, 503 model not loaded |
| POST | `/optimize` | 200, including `INFEASIBLE` | 400 malformed JSON, 422 validation, 500 optimizer failure |

### GET /health

No inference is performed: it reports the load state from startup plus a file-existence check.

```json
{"status":"UP","service":"agrioptima-ml","version":"0.6.0",
 "model":{"status":"UP","artifact_available":true,"loaded":true,
          "model_version":"yield-lds2018-xgboost-20260927","error":null},
 "optimizer":"UP"}
```
Without a usable artifact: `"status":"DEGRADED"`, `"model":{"status":"DOWN","artifact_available":false,"loaded":false,
"model_version":null,"error":"model metadata not found"}`. `/optimize` keeps working. Possible `error` values:
`model metadata not found`, `model metadata is invalid`, `model artifact not found`,
`model artifact failed its sha256 integrity check`, `model artifact does not match this service's feature contract`,
`model artifact could not be deserialised`. File paths appear only in the server log.

### GET /model/info

Everything comes from `artifacts/metadata.json` of the loaded artifact: `model_version`, `model_type` (`xgboost`),
`model_description`, `target` (unit t/ha), `supported_crops`, `feature_version` (sha256 fingerprint of the feature
contract recorded in the artifact, e.g. `features-sha256:d4cc7b09af9ce601`), `features`, `scenario_features`,
`categories` (accepted values), `supported_ranges`, `training` (datasets with handles/versions/licences,
processed-data md5 and row count, split, CV method, seed, hyperparameters, training commit, library versions),
`evaluation` (CV, held-out test, per crop, baseline, random-split comparison), `artifact` (`sha256` verified at
startup, `bytes`), and `limitations`. `?crop=maize` adds `"requested_crop":{"crop":"MAIZE","supported":false}`.
The response contains no paths or file names.

### POST /predict-yield

A batch of 1–50 scenarios, for example one per candidate plan plus current practice. Answers keep the request order.

```json
{"scenarios":[
  {"crop":"WHEAT","state":"BIHAR","sowing_date":"2025-11-20","soil_texture":"MEDIUM","variety_type":"IMPROVED",
   "previous_crop":"RICE","irrigation_available":true,"n_kg_ha":120,"p2o5_kg_ha":60,"k2o_kg_ha":40},
  {"crop":"WHEAT","state":"BIHAR","sowing_date":"2025-11-20","irrigation_available":true,
   "n_kg_ha":400,"p2o5_kg_ha":60,"k2o_kg_ha":40}]}
```
Actual response (uvicorn, 2026-09-27):
```json
{"model_version":"yield-lds2018-xgboost-20260927","feature_version":"features-sha256:d4cc7b09af9ce601","unit":"t/ha",
 "predictions":[
  {"predicted_yield_t_ha":3.107,"extrapolation":false,"clipped_features":[],"clipped_inputs":[],
   "model_version":"yield-lds2018-xgboost-20260927","index":0,"crop":"WHEAT"},
  {"predicted_yield_t_ha":3.257,"extrapolation":true,"clipped_features":["n_kg_ha"],
   "clipped_inputs":[{"feature":"n_kg_ha","input_value":400.0,"used_value":217.38,
                      "supported_min":27.02,"supported_max":217.38}],
   "model_version":"yield-lds2018-xgboost-20260927","index":1,"crop":"WHEAT"}],
 "extrapolation":true,
 "warnings":["Scenario 1: n_kg_ha = 400 is outside the supported range for WHEAT [27.02, 217.38]; the model used 217.38, so this prediction is an extrapolation and does not reflect the requested value."],
 "disclaimer":"Observational model trained on farmer-reported yields. ... Held-out-district RMSE: wheat 0.7133 t/ha, rice 1.4024 t/ha."}
```
Field rules come from the Milestone 3 `YieldScenario`: `state` must be one of the 8 training states; `soil_texture`
LIGHT/MEDIUM/HEAVY, `variety_type` IMPROVED/HYBRID/LOCAL (both optional); doses 0–1000 kg/ha and finite; unknown
fields are rejected. Values are normalised as in training (`"wheat"` → WHEAT, `"Bihar"` → BIHAR,
`"Lentil"` → PULSE).

**Unsupported crop** (any crop not in the artifact's `supported_crops`, e.g. MAIZE): the whole batch is rejected and
nothing is predicted.
```json
HTTP 422  application/problem+json
{"type":"urn:agrioptima:problem:unsupported-crop","title":"Unsupported crop","status":422,
 "detail":"The yield model supports WHEAT, RICE only; no prediction is made for MAIZE.",
 "instance":"/predict-yield","errors":{"scenarios[0].crop":"unsupported crop 'MAIZE'"}}
```

### POST /optimize

The request is the Milestone 5 `OptimizationRequest`, unchanged (the backend's `FertilizerPlanVerifier` contract):
```json
{"requirement_kg_ha":{"n":67.5,"p2o5":45,"k2o":22.5},"area_ha":0.5,
 "fertilizers":[{"code":"UREA","name":"Urea","n_pct":46,"p2o5_pct":0,"k2o_pct":0,"price_per_kg":5.92},
                {"code":"DAP","n_pct":18,"p2o5_pct":46,"k2o_pct":0,"price_per_kg":27.0}, "..."],
 "parameters":{"balanced_budget_share":0.5,"default_max_kg_ha":500}}
```
`parameters` and `max_kg_ha` are optional. The response is the optimizer's `OptimizationResult` (`status`, `area_ha`,
`requirement_kg_ha`, `requirement_field_kg`, `plans[]`, `infeasibility[]`, `warnings`, `parameters`, `solver`,
`disclaimer`) plus:

- `feasible`: false only for `INFEASIBLE`
- `infeasibility_reason`: one-line summary of `infeasibility`, otherwise null
- `units`: the unit of each numeric field

Each plan contains `strategy`, `objective` (expression, tie-break, value, unit), `items[]` (`code`, `name`, `kg_ha`,
`field_kg`, `cost_per_ha`, `field_cost`, `supplied_kg_ha`, `at_upper_bound`), `supplied_*`, `excess_*`,
`total_excess_kg_ha`, `cost_per_ha`, `field_cost`, `total_mass_*`, `feasible` and `same_as`.

Actual result for the rice example above (V2 catalogue): all three strategies choose DAP 48.914 + NPK 10:26:26 86.539 +
Urea 108.787 kg/ha, costing ₹4,508.94/ha and ₹2,254.47 for the 0.5 ha field (`same_as` lists the other two strategies).

**Infeasible is a result, not an error.** It returns 200 because the request was valid and the answer is "no plan
exists". The backend needs the per-nutrient reason to explain this to the farmer.
```json
{"status":"INFEASIBLE","feasible":false,"plans":[],
 "infeasibility":[{"nutrient":"K2O","required_kg_ha":20.0,"max_supply_kg_ha":0.0,"shortfall_kg_ha":20.0,
                   "reason":"no available fertilizer contains K2O"}],
 "infeasibility_reason":"K2O: requires 20 kg/ha but at most 0 kg/ha can be supplied (no available fertilizer contains K2O)", "...":"..."}
```
Invalid requirements (negative, > 1000 kg/ha, NaN/∞, missing or extra nutrient) and invalid fertilizer definitions
(duplicate codes, grade > 100 % or N+P2O5+K2O > 100, negative price or cap, empty code, unknown fields, > 50 products)
return 422 with the field path in `errors`.

## 4. Errors

All errors are RFC 7807 `application/problem+json` with `type = urn:agrioptima:problem:<kind>`, `title`, `status`,
`detail`, `instance` (the request path) and, for validation errors, `errors` (field path → message). They never
contain stack traces, exception text, file paths or the rejected input value.

| Status | `type` kind | When |
|---|---|---|
| 400 | `malformed-request` | body is not valid JSON |
| 422 | `validation` | schema violation, e.g. `{"requirement_kg_ha.n":"Input should be greater than or equal to 0","area_ha":"Input should be greater than 0"}` |
| 422 | `unsupported-crop` | crop the model was not trained on |
| 404 / 405 | `not-found` / `method-not-allowed` | unknown route / wrong method |
| 500 | `optimization-failed` | solver failure or a plan that failed post-solve verification (never returned as a plan) |
| 500 | `internal` | anything unexpected (details only in the server log) |
| 503 | `model-unavailable` | yield model not loaded (`detail` gives the safe reason) |

Validation uses status 422, the FastAPI convention; the backend uses 400 for its own validation errors. The Milestone 7
client should treat any 4xx from the ML service as a bug in the request it built.

## 5. Configuration

| Variable | Default | Meaning |
|---|---|---|
| `MODEL_ARTIFACT_DIR` | `ml-service/artifacts` | directory with `metadata.json` + model file |
| `ML_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | comma-separated exact origins; empty disables CORS; `*` is refused at startup |

CORS allows only GET/POST, the `Content-Type` header and no credentials. The service is meant to be reachable only
from the backend (Docker network in Milestone 12). It has no authentication of its own.

## 6. Run and test

```powershell
.\scripts\ml.ps1 serve   # http://localhost:8001/docs
.\scripts\ml.ps1 test    # 188 tests (90 of them API tests in tests/test_api.py)
```
