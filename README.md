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

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Project status](PROJECT_STATUS.md)

_Setup, dataset sources, model evaluation, API docs and deployment sections are added as the
corresponding milestones land._
