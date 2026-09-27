# AgriOptima — Project Status

_Last updated: 2026-09-27_

## Current milestone

**Milestone 1 — Requirements + architecture: COMPLETE**
Next: **Milestone 2 — Database + backend foundation**

## Milestones

| # | Milestone | Status |
|---|---|---|
| 1 | Requirements + architecture | ✅ Done |
| 2 | Database + backend foundation | ⏳ Next |
| 3 | Data acquisition + ML pipeline | Pending |
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

## Completed

- System, component, data-flow, domain, ML, optimization and API architecture — `docs/ARCHITECTURE.md`
- Monorepo skeleton, `.gitignore`, `.env.example`
- Toolchain: portable JDK 21 in `.tools/` (git-ignored); Python venv for `ml-service/` with
  pandas, numpy, scikit-learn, xgboost, scipy, fastapi, pytest

## Pending (next up)

- Spring Boot project (Maven wrapper, Java 21), Flyway schema, entities, JWT auth, farm/field/soil CRUD,
  Swagger, first JUnit tests

## Known issues / environment constraints

- System Java is 1.8; the backend must be built with `.tools/jdk-21*` (scripts set `JAVA_HOME`).
- Docker and MySQL are not installed on the dev machine → `dev` profile uses H2 (MySQL mode);
  `mysql` profile is used by Docker Compose. Compose must be validated on a machine with Docker.

## Architecture decisions (ADR log)

| # | Decision | Reason |
|---|---|---|
| 1 | Optimizer (SciPy LP) lives in the FastAPI service next to the yield model | Stack mandates SciPy; all numerical code in one tested service. Spring re-verifies constraints. |
| 2 | Nutrient-requirement engine is deterministic Java in Spring Boot | Transparent, JUnit-testable, owns the hard constraints the ML/optimizer must respect. |
| 3 | Optimization is a linear program (`linprog`, HiGHS), plans from 3 weight profiles | Exact, fast, explainable; no black-box heuristics. |
| 4 | `OptimizationRun` + `YieldPrediction` folded into `recommendation_plans` | One row per candidate plan carries its optimizer output and prediction; fewer joins, same info. |
| 5 | Weather: Open-Meteo | Free, no API key → no credential risk during the demo. |
| 6 | Explanation is a separate endpoint with a template fallback | Recommendation never blocked by LLM latency/outage. |
| 7 | Flyway for schema migrations | Reproducible schema with explicit FKs, constraints and indexes on both MySQL and H2. |
| 8 | Nutrient units: kg/ha of N, P2O5, K2O | Matches fertilizer-grade labelling convention. |

## Dataset decisions

_Not yet made (Milestone 3)._ Requirement: yield target + applied-fertilizer features.
If no adequate public dataset exists, a clearly labelled synthetic dataset will be generated.

## ML model version

_None yet._

## Important assumptions

- Optimizer penalty weights (λ) are tuning weights in ₹/kg, not scientific constants.
- Sustainability score (to be defined in M4/M9) is a prototype metric, not an official standard.
- All recommendations are estimates for a hackathon prototype and must be validated with soil tests and
  qualified local agronomic guidance.
