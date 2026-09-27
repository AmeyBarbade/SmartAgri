# AgriOptima

**AI-Powered Sustainable Fertilizer Optimization** — a hackathon decision-support prototype that turns
soil tests, crop stage, weather and previous fertilizer use into an optimized, explained fertilizer plan.

> ⚠️ Prototype. Recommendations are estimates based on available data and must be validated with soil
> testing and qualified local agricultural guidance before real-world use.

## How it works

```
Soil + Crop + Stage + Weather + Previous usage
  → Nutrient requirement engine (deterministic)
  → LP optimizer (SciPy) → candidate plans A/B/C
  → ML yield prediction for each plan
  → Transparent scoring → selected plan + schedule + warnings
  → LLM explanation grounded in those results
```

## Tech stack

| Layer | Tech |
|---|---|
| Frontend | React, Vite, Tailwind CSS, Recharts, Axios, React Router |
| Backend | Java 21, Spring Boot 3, Spring Data JPA, Spring Security + JWT, Bean Validation, Flyway |
| Database | MySQL 8 (H2 in MySQL mode for zero-setup local dev) |
| ML / optimization | Python, pandas, NumPy, scikit-learn, XGBoost, SciPy, FastAPI |
| External | Open-Meteo weather API, LLM API (explanations only) |
| Testing | JUnit 5, Mockito, pytest |
| DevOps | Docker, Docker Compose, OpenAPI / Swagger UI |

## Repository layout

```
frontend/     React app
backend/      Spring Boot API
ml-service/   FastAPI: yield model + optimizer, training scripts
data/         raw / processed data + agronomic knowledge base
docs/         architecture, data card, model card, demo script
scripts/      dev helpers
docker/       container config
```

## Local setup

### Backend (works without MySQL or Docker)

Requires the portable JDK 21 in `.tools/` (the machine's system Java 8 is not used):

```bash
mkdir -p .tools && cd .tools
curl -L -o jdk21.zip "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
unzip -q jdk21.zip && rm jdk21.zip
```

Then, from the repo root:

```powershell
.\scripts\backend.ps1 run     # http://localhost:8080  (dev profile: H2 file DB in backend/data/)
.\scripts\backend.ps1 test    # full test suite
```

(`scripts/backend.sh run|test|build` in Git Bash / Linux / macOS.)

- Swagger UI: http://localhost:8080/swagger-ui.html: register or log in, then **Authorize** with the token
- H2 console (dev only): http://localhost:8080/h2-console, JDBC URL `jdbc:h2:file:./data/agrioptima-dev`, user `sa`

Profiles: `dev` (default, H2 in MySQL mode) and `mysql` (Docker Compose / deployment; needs `MYSQL_*` and
`JWT_SECRET`, see `.env.example`). Schema is managed by Flyway (`backend/src/main/resources/db/migration`).

### ML service (Python 3.12+)

```powershell
cd ml-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
cd ..
.\scripts\ml.ps1 pipeline   # download real survey data (MD5-checked) -> validate/clean -> train + evaluate
.\scripts\ml.ps1 test       # pytest
.\scripts\ml.ps1 serve      # ML API on http://localhost:8001 (Swagger UI: /docs)
.\scripts\ml.ps1 optimize   # fertilizer optimizer on examples\optimizer\m4_6_wheat_tillering_pk_only_1ha.json
```

(`scripts/ml.sh download|prepare|train|pipeline|test|serve|optimize [request.json]` in Git Bash / Linux / macOS.) A trained model is
committed in `ml-service/artifacts/`, so `test` and `serve` work without re-running the pipeline.

### Run both services (end-to-end recommendation)

```powershell
# terminal 1 - ML service (FastAPI) on http://localhost:8001
.\scripts\ml.ps1 serve
# terminal 2 - backend (Spring Boot, H2) on http://localhost:8080
.\scripts\backend.ps1 run
# terminal 3 - demo: creates a user with wheat, rice and maize fields and runs a recommendation for each
ml-service\.venv\Scripts\python.exe scripts\demo_recommendation.py            # add --json wheat_pk_only for a full response
```
The backend finds the ML service through `ML_SERVICE_BASE_URL` (default `http://localhost:8001`). Without the ML
service, recommendations return 503. Everything else still works. To run the backend test against the real ML
service as well: `$env:ML_LIVE_BASE_URL="http://localhost:8001"; .\scripts\backend.ps1 test`.

### React dashboard (Node 20+)

```powershell
# with the ML service and backend running (above), once: create the fixed demo account, fields and recommendations
ml-service\.venv\Scripts\python.exe scripts\demo_recommendation.py --seed-demo-user
cd frontend
npm install
npm run dev      # http://localhost:5173 - sign in as demo@agrioptima.local / demo-pass-123
npm test         # Vitest + Testing Library
npm run build    # production bundle in frontend/dist
```
In development, Vite proxies `/api` to `http://localhost:8080` (override with `BACKEND_URL`). Set `VITE_API_BASE_URL`
to call a backend directly instead (its CORS allows `http://localhost:5173`). The React app calculates nothing: every
requirement, plan, yield and score shown comes from the backend response.

## Recommendations (end to end)

`POST /api/fields/{id}/recommendations` runs the whole pipeline: nutrient requirement → three optimizer plans (each
re-verified in Java) → predicted yield per plan → transparent score (revenue − fertilizer cost − excess penalty) →
selected plan. The run is stored; `GET /api/fields/{id}/recommendations` returns the history and
`GET /api/recommendations/{id}` a stored run. Put the state in the farm location (e.g. "Patna, Bihar") and set a
sowing date to get yield predictions. See [docs/RECOMMENDATION_FLOW.md](docs/RECOMMENDATION_FLOW.md).

## Nutrient requirement engine

`GET /api/fields/{id}/nutrient-requirement` computes a deterministic N / P2O5 / K2O requirement for a field at its
current growth stage. It uses a versioned, sourced knowledge base
(`backend/src/main/resources/knowledge/nutrient-kb-v1.json`), the latest soil test and recorded fertilizer
applications. Every value is labelled as referenced, mapped or prototype assumption. See
[docs/RECOMMENDATION_ENGINE.md](docs/RECOMMENDATION_ENGINE.md).

## Fertilizer optimizer

`ml-service/app/optimizer.py` turns the requirement into fertilizer quantities. It solves three transparent linear
programs with SciPy `linprog` / HiGHS: **lowest cost**, **minimum nutrient excess**, and **balanced** (least excess
for at most half the extra cost). Every plan meets the requirement exactly after rounding to 1 g/ha, and the backend
re-checks it independently (`FertilizerPlanVerifier`). The optimizer does not use the ML model or an LLM, and it never
changes the requirement. See [docs/OPTIMIZER.md](docs/OPTIMIZER.md).

## ML service API

Internal FastAPI service (port 8001) called by the backend: `GET /health`, `GET /model/info`,
`POST /predict-yield` (batch yield prediction in t/ha; wheat and rice only, and out-of-range inputs are clipped and
flagged) and `POST /optimize` (the three fertilizer plans in kg/ha and INR). Errors are RFC 7807 problem+json. CORS
origins are set with `ML_CORS_ALLOWED_ORIGINS`. See [docs/ML_API.md](docs/ML_API.md).

## Data and model

The yield model is trained on **real public survey data**: the CIMMYT CSISA Landscape Diagnostic Surveys
2018 for wheat (7,648 plots) and rice (8,355 plots) in India, which record the fertilizer products and amounts
each farmer actually applied, plus yield. No synthetic data is used. The model predicts yield (t/ha) for a
candidate fertilizer plan. It ranks plans that the rule engine and optimizer produce and never prescribes doses.
Current model `yield-lds2018-xgboost-20260927`: MAE 0.55 t/ha (wheat) and 1.17 t/ha (rice) on districts not
seen in training. Data is observational and yields are farmer-reported; see the data and model cards for
limitations.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Project status](PROJECT_STATUS.md)
- [Data card](docs/DATA_CARD.md): dataset investigation, selection, cleaning, features, limitations
- [Model card](docs/MODEL_CARD.md): model comparison, metrics, selection, reproducibility
- [Recommendation engine](docs/RECOMMENDATION_ENGINE.md): knowledge base, sources, calculation, examples
- [Optimizer](docs/OPTIMIZER.md): LP formulation, objectives, assumptions, tolerances, actual results
- [ML service API](docs/ML_API.md): endpoints, units, request/response examples, errors, configuration
- [Recommendation flow](docs/RECOMMENDATION_FLOW.md): end-to-end pipeline, model inputs, scoring, example response, errors
- Generated evaluation report: `ml-service/reports/evaluation_report.md`

_API docs and deployment sections are added as the corresponding milestones land._
