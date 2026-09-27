# End-to-end recommendation flow (Milestone 7)

`POST /api/fields/{fieldId}/recommendations[?profile=CODE]` (JWT, owner only) runs the whole core pipeline, stores
the result and returns it. This response is the main data contract for the React dashboard.

## 1. Pipeline

```
1  FieldService.requireOwned                    404 if the field is missing or belongs to another user
2  NutrientRequirementService (Milestone 4)     crop + stage required (400); latest soil test (optional); applications
                                                -> requirementForOptimizer.kgPerHa (the only requirement used)
3  read-only snapshot                           field, farm location, active fertilizer catalogue
4  ML POST /optimize (Milestone 5/6)            requirement + area + catalogue (per-product cap 500 kg/ha)
     INFEASIBLE -> stored and returned with feasible=false, reasons, warnings, assumptions; no yield prediction
5  FertilizerPlanVerifier (Milestone 5, Java)   every plan re-checked from the backend's own catalogue;
                                                failing plans are dropped (warning); none left -> 502
6  ML POST /predict-yield (Milestone 3/6)       one scenario per verified plan (batch)
     unavailable (maize, model down, missing state/sowing date) -> plans kept, ranked without revenue
7  PlanScoringService                           transparent score, deterministic tie-break, selects one plan
8  persist (recommendations + recommendation_plans), return 201
```

`RecommendationService` only orchestrates. The requirement, the plans and the yields come from the existing
components, unchanged. No database transaction is open during the HTTP calls. The plan quantities are the optimizer's.
The nutrients, cost and mass shown for each plan are the verifier's recomputed values.

## 2. Yield model inputs (feature contract of Milestone 3)

| Model input | Where it comes from | Source label |
|---|---|---|
| crop | field crop | FIELD |
| state | a supported state name found in the farm's `locationName` (e.g. "Patna, **Bihar**"); none or several → **no prediction** | MAPPED |
| sowing_date | field sowing date; missing → **no prediction** | FIELD |
| soil_texture | field `soilType` text: clay/black/vertisol → HEAVY, sand → LIGHT, loam/silt/alluvial → MEDIUM (in that order); unmapped → sent empty | MAPPED / NOT_RECORDED |
| irrigation_available | IRRIGATED → true, RAINFED → false; not set → true (same default as the requirement engine's non-rainfed profile) | FIELD / PROTOTYPE_DEFAULT |
| variety_type | not stored → sent empty; the model pipeline imputes the most frequent training value | NOT_RECORDED |
| previous_crop | field `previousCrop` text (grouped by the ML service); empty → imputed | FIELD / NOT_RECORDED |
| fym_applied | not stored → false | PROTOTYPE_DEFAULT |
| zn_applied | false: no plan contains a zinc product (none in the catalogue) | DERIVED |
| n / p2o5 / k2o_kg_ha | **season totals**: applied this season + the plan + later splits of the schedule (remaining season − due now) | DERIVED |

The model was trained on season-total doses, which is why the scenario is not just the plan. Every input and its source
is listed in `yieldPrediction.inputs`. Nothing farmer-specific is invented: when state or sowing date is missing, the
response says so and no yield is predicted.

## 3. Plan scoring (prototype)

```
score (INR/ha) = predicted yield (t/ha) × crop price (INR/t) − fertilizer cost (INR/ha) − 20 INR/kg × total N+P2O5+K2O excess (kg/ha)
```

| Parameter | Value | Status |
|---|---|---|
| Crop price | wheat 24,250, rice 23,690, maize 24,000 INR/t (GoI MSP 2025-26: ₹2,425 / ₹2,369 / ₹2,400 per quintal) | PROTOTYPE_ASSUMPTION, indicative (farm-gate prices differ) |
| Excess penalty | 20 INR per kg excess nutrient, the same order as buying it (urea N ≈ ₹12.9/kg) | PROTOTYPE_ASSUMPTION |
| Per-product cap | 500 kg/ha | optimizer's prototype default |

All three are set in `application.yml` (`app.recommendation.*`) and reported in every response (`scoring`).

- **Without a yield for every plan**, revenue is dropped for all plans (`COST_AND_EXCESS_ONLY`): score = −cost − penalty.
- **Tie-break:** a score equal to the paisa → lower cost → lower total excess → lower product mass → the fixed order
  LOWEST_COST, MIN_EXCESS, BALANCED. The optimizer often returns identical plans (`sameAs`), so ties are common.
- **Scoring cannot change plans:** it only reads immutable figures of verified plans. It cannot create, resize or
  alter a plan, or bypass the requirement.

## 4. Actual results (real FastAPI service, `scripts/demo_recommendation.py`, 2026-09-27)

| Case | Requirement N/P2O5/K2O kg/ha | Plans | Predicted yield | Selected |
|---|---|---|---|---|
| Wheat CRI, 2 ha, basal applied, Patna, Bihar | 40 / 0 / 0 | all: Urea 86.957 kg/ha, ₹514.79/ha | 3.25 t/ha | LOWEST_COST (3-way tie) |
| Rice PI, 0.5 ha, high soil | 67.5 / 45 / 22.5 | all: DAP 48.914 + NPK 86.539 + Urea 108.787, ₹4,508.94/ha | 3.967 t/ha | LOWEST_COST (tie) |
| Wheat tillering, 1 ha, 400 kg urea applied | 0 / 60 / 40 | LC ₹5,697.03, 23.21 kg N excess · ME ₹6,391.68, 0 excess · BAL ₹6,044.37, 10.83 excess | 3.317 / 3.234 / 3.292 | LOWEST_COST, score ₹74,275.98 vs 73,570.09 vs 72,032.82 |
| Maize sowing, 1.2 ha, rainfed, no soil test | 30 / 30 / 30 | all: NPK 115.385 + Urea 40.134, ₹3,629.91/ha | n/a (unsupported crop) | LOWEST_COST, cost-and-excess mode |

In the wheat P+K case, LOWEST_COST wins partly because its 23 kg/ha of surplus N *raises* the predicted yield
(3.317 vs 3.234 t/ha). The yield model is observational and cannot penalise over-application
([MODEL_CARD.md](MODEL_CARD.md)). The ₹20/kg penalty (₹464/ha here) is small next to that revenue effect. Raising the
penalty is a one-line configuration change; the choice is a product decision.

Example response (wheat P+K case, shortened):
```json
{"id":71,"createdAt":"2026-09-27T12:45:34.594887","status":"OPTIMAL","feasible":true,
 "field":{"id":135,"name":"Wheat - P+K only","farmName":"Demo farm","location":"Patna, Bihar","areaHa":1.0,
          "soilType":"Loam","irrigationType":"IRRIGATED","season":"RABI","sowingDate":"2025-11-20","previousCrop":"Rice"},
 "crop":{"code":"WHEAT","name":"Wheat"},"growthStage":{"code":"TILLERING","name":"Tillering","seq":3},
 "soil":{"soilTestUsed":true,"sampleDate":"2025-11-10","ageDays":321,"availableNKgHa":300.0,"availablePKgHa":15.0,
         "availableKKgHa":200.0,"ph":7.0,"organicCarbonPct":0.6,"nClass":"MEDIUM","pClass":"MEDIUM","kClass":"MEDIUM"},
 "requirement":{"dueNowKgHa":{"n":0.0,"p2o5":60.0,"k2o":40.0},"dueNowFieldKg":{"n":0.0,"p2o5":60.0,"k2o":40.0},
                "alreadyAppliedKgHa":{"n":184.0,"p2o5":0.0,"k2o":0.0},"remainingSeasonKgHa":{"n":0.0,"p2o5":60.0,"k2o":40.0},
                "profileCode":"IRRIGATED_TIMELY_SOWN","profileName":"Irrigated, timely sown - NHZ, CZ, PZ, SHZ"},
 "plans":[{"strategy":"LOWEST_COST","label":"Lowest cost","description":"Cheapest combination that meets the requirement.",
   "selected":true,"feasible":true,
   "items":[{"code":"DAP","name":"Diammonium Phosphate (DAP)","kgHa":43.479,"fieldKg":43.479,"bagKg":50.0,"fieldBags":0.9,
             "costPerHa":1173.93,"fieldCost":1173.93},
            {"code":"NPK_10_26_26","name":"NPK Complex 10:26:26","kgHa":153.847,"fieldKg":153.847,"bagKg":50.0,
             "fieldBags":3.1,"costPerHa":4523.1,"fieldCost":4523.1}],
   "suppliedKgHa":{"n":23.21,"p2o5":60.0,"k2o":40.0},"suppliedFieldKg":{"n":23.21,"p2o5":60.0,"k2o":40.0},
   "excessKgHa":{"n":23.21,"p2o5":0.0,"k2o":0.0},"totalExcessKgHa":23.212,"costPerHa":5697.03,"fieldCost":5697.03,
   "totalMassKgHa":197.326,"totalMassFieldKg":197.326,"sameAs":[],
   "yield":{"available":true,"predictedYieldTHa":3.317,"fieldProductionT":3.317,"extrapolation":false,
            "clippedFeatures":[],"seasonNutrientsKgHa":{"n":207.21,"p2o5":60.0,"k2o":40.0}},
   "score":{"expectedRevenuePerHa":80437.25,"fertilizerCostPerHa":5697.03,"excessPenaltyPerHa":464.23,
            "scorePerHa":74275.98,"rank":1}},
  {"strategy":"MIN_EXCESS","...":"same shape"},{"strategy":"BALANCED","...":"same shape"}],
 "selectedPlan":{"strategy":"LOWEST_COST","label":"Lowest cost",
   "reason":"LOWEST_COST has the highest score: 74275.98 INR/ha (expected revenue 80437.25 - fertilizer 5697.03 - excess penalty 464.23)."},
 "scoring":{"mode":"REVENUE_MINUS_COST_AND_EXCESS","formula":"score (INR/ha) = predicted yield (t/ha) x crop price (INR/t) - ...",
            "cropPriceInrPerTonne":24250,"cropPriceSource":"GoI MSP 2025-26, indicative","excessPenaltyInrPerKg":20.0,
            "tieBreak":"equal score (to 0.01 INR/ha) -> lower cost -> lower total excess -> lower product mass -> LOWEST_COST, MIN_EXCESS, BALANCED"},
 "yieldPrediction":{"available":true,"modelVersion":"yield-lds2018-xgboost-20260927",
   "inputs":[{"feature":"state","value":"BIHAR","source":"MAPPED","note":"found in the farm location 'Patna, Bihar'"},"..."]},
 "infeasibility":[],
 "warnings":["N already applied this season (184.0 kg/ha) exceeds the adjusted season target (120.0 kg/ha) by 64.0 kg/ha. ...","..."],
 "assumptions":["General dose from profile IRRIGATED_TIMELY_SOWN [REFERENCED, source IIWBR_EB52]. ...","..."],
 "knowledgeBase":{"id":"agrioptima-nutrient-kb","version":"1.0.0","status":"PROTOTYPE"},
 "modelVersion":"yield-lds2018-xgboost-20260927","optimizerSolver":"scipy.optimize.linprog (highs-ds, SciPy 1.18.1)",
 "disclaimer":"Prototype knowledge base for a hackathon decision-support tool. ..."}
```

## 5. Errors

| Situation | Response |
|---|---|
| Field of another user / unknown | 404 `not-found` (the ML service is never called) |
| No token | 401 |
| Field without crop or growth stage, bad `profile` | 400 |
| No soil test | 201, engine default (ASSUMED_MEDIUM) + warning |
| Infeasible requirement | **201**, `feasible:false`, `infeasibilityReason`, `infeasibility[]`, stored |
| ML service not reachable | 503 `ml-service-unavailable` |
| ML timeout (read 15 s, connect 2 s) | 504 `ml-service-timeout` |
| ML error status on /optimize | 502 `ml-service-error` |
| Unparseable / inconsistent optimizer answer, or no plan passes the verifier | 502 `ml-service-invalid-response` |
| Yield prediction fails (unsupported crop, model not loaded, timeout, bad answer) | 201, plans ranked without revenue, reason in `yieldPrediction.unavailableReason` + warning |

Problem details never contain the ML URL or upstream error text (logged server-side only). Failed runs are not stored.

## 6. Persistence (Flyway V3)

`recommendations` stores the key figures as columns (status, feasible, crop/stage, area, required N/P2O5/K2O,
selected strategy, scoring mode, KB and model versions) plus the full response as JSON (`response_json`). A stored
recommendation is therefore shown exactly as computed, even after prices or models change. `recommendation_plans`
holds one summary row per plan (cost, field cost, excess, mass, predicted yield, score, selected).
`ON DELETE CASCADE` from fields. Plan line items are only in the JSON for now.

Reads: `GET /api/fields/{fieldId}/recommendations` (history, newest first) and `GET /api/recommendations/{id}`
(the stored response).

## 7. Configuration

| Property | Env var | Default |
|---|---|---|
| `app.ml.base-url` | `ML_SERVICE_BASE_URL` | `http://localhost:8001` |
| `app.ml.connect-timeout` / `read-timeout` | `ML_SERVICE_CONNECT_TIMEOUT` / `ML_SERVICE_READ_TIMEOUT` | 2s / 15s |
| `app.recommendation.crop-price-inr-per-tonne.*`, `excess-penalty-inr-per-kg`, `max-kg-ha-per-product` | — | see §3 |

The HTTP client is `java.net.http.HttpClient` (HTTP/1.1). `HttpURLConnection` was replaced after a test showed that
it silently re-sends a POST after a read timeout.
