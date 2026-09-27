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

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Project status](PROJECT_STATUS.md)

_Setup, dataset sources, model evaluation, API docs and deployment sections are added as the
corresponding milestones land._
