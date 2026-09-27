"""HTTP routes. Each one validates via its schema, calls one function in app.services and returns the result."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Body, Depends, Query, Request

from app import services
from app.api.schemas import (
    HealthResponse,
    ModelInfoResponse,
    OptimizeResponse,
    PredictYieldRequest,
    PredictYieldResponse,
)
from app.errors import ERROR_RESPONSES, PROBLEM_JSON, Problem
from app.optimizer import OptimizationRequest
from app.services import ModelState

router = APIRouter()

MODEL_UNAVAILABLE = {503: {"model": Problem, "description": "Yield model not loaded (see /health)",
                           "content": {PROBLEM_JSON: {}}}}
UNSUPPORTED_CROP = {"model": Problem, "content": {PROBLEM_JSON: {}},
                    "description": "Validation failed, or `unsupported-crop` (e.g. MAIZE): no prediction is made"}


def model_state(request: Request) -> ModelState:
    return request.app.state.model_state


State = Annotated[ModelState, Depends(model_state)]

_WHEAT = {"crop": "WHEAT", "state": "BIHAR", "sowing_date": "2025-11-20", "soil_texture": "MEDIUM",
          "variety_type": "IMPROVED", "previous_crop": "RICE", "irrigation_available": True,
          "n_kg_ha": 120, "p2o5_kg_ha": 60, "k2o_kg_ha": 40}
PREDICT_EXAMPLES = {
    "wheat": {"summary": "Wheat, Bihar, two candidate plans",
              "value": {"scenarios": [_WHEAT, {**_WHEAT, "n_kg_ha": 150, "p2o5_kg_ha": 75, "k2o_kg_ha": 50}]}},
    "extrapolation": {"summary": "N above the supported range (clipped and flagged)",
                      "value": {"scenarios": [{**_WHEAT, "n_kg_ha": 400}]}},
    "maize": {"summary": "Unsupported crop (422 unsupported-crop)",
              "value": {"scenarios": [{**_WHEAT, "crop": "MAIZE"}]}},
}
_V2 = [
    {"code": "UREA", "name": "Urea", "n_pct": 46, "p2o5_pct": 0, "k2o_pct": 0, "price_per_kg": 5.92},
    {"code": "DAP", "name": "Diammonium Phosphate (DAP)", "n_pct": 18, "p2o5_pct": 46, "k2o_pct": 0,
     "price_per_kg": 27.00},
    {"code": "MOP", "name": "Muriate of Potash (MOP)", "n_pct": 0, "p2o5_pct": 0, "k2o_pct": 60, "price_per_kg": 34.00},
    {"code": "NPK_10_26_26", "name": "NPK Complex 10:26:26", "n_pct": 10, "p2o5_pct": 26, "k2o_pct": 26,
     "price_per_kg": 29.40},
    {"code": "SSP", "name": "Single Super Phosphate (SSP)", "n_pct": 0, "p2o5_pct": 16, "k2o_pct": 0,
     "price_per_kg": 11.00},
]
OPTIMIZE_EXAMPLES = {
    "pk_only": {"summary": "Wheat P+K only, 1 ha (the three plans differ)",
                "value": {"requirement_kg_ha": {"n": 0, "p2o5": 60, "k2o": 40}, "area_ha": 1.0, "fertilizers": _V2}},
    "rice": {"summary": "Rice panicle initiation, 0.5 ha",
             "value": {"requirement_kg_ha": {"n": 67.5, "p2o5": 45, "k2o": 22.5}, "area_ha": 0.5,
                       "fertilizers": _V2}},
    "infeasible": {"summary": "No potash source (INFEASIBLE, 200)",
                   "value": {"requirement_kg_ha": {"n": 40, "p2o5": 30, "k2o": 20}, "area_ha": 1.0,
                             "fertilizers": _V2[:2]}},
}


@router.get("/health", response_model=HealthResponse, tags=["service"], summary="Service health (no inference)")
def health(state: State) -> HealthResponse:
    """Always 200 while the process runs. `status` is `DEGRADED` when the yield model is not loaded; `/optimize`
    still works then."""
    return services.health(state)


@router.get("/model/info", response_model=ModelInfoResponse, response_model_exclude_none=True, tags=["model"],
            summary="Metadata of the loaded yield model", responses=MODEL_UNAVAILABLE)
def model_info(state: State,
               crop: Annotated[str | None, Query(max_length=40, description="Report whether this crop is supported",
                                                 examples=["MAIZE"])] = None) -> ModelInfoResponse:
    return services.model_info(state, crop)


@router.post("/predict-yield", response_model=PredictYieldResponse, tags=["model"],
             summary="Predict grain yield (t/ha) for a batch of fertilizer scenarios",
             responses={**ERROR_RESPONSES, 422: UNSUPPORTED_CROP, **MODEL_UNAVAILABLE})
def predict_yield(state: State,
                  body: Annotated[PredictYieldRequest, Body(openapi_examples=PREDICT_EXAMPLES)]) -> PredictYieldResponse:
    """Uses the verified artifact and the training preprocessing (app/features.py). Numeric inputs outside the
    training range are clipped to the nearest bound; the response then says `extrapolation: true` and lists each
    clipped input with the value used. Wheat and rice only."""
    return services.predict_yield(state, body)


@router.post("/optimize", response_model=OptimizeResponse, tags=["optimizer"],
             summary="Candidate fertilizer plans that meet a nutrient requirement",
             responses=ERROR_RESPONSES)
def optimize(body: Annotated[OptimizationRequest, Body(openapi_examples=OPTIMIZE_EXAMPLES)]) -> OptimizeResponse:
    """Runs the Milestone 5 LP optimizer (SciPy/HiGHS) and returns LOWEST_COST, MIN_EXCESS and BALANCED plans.
    Quantities are kg product/ha (field totals in kg), nutrients kg/ha, costs INR/ha and INR per field.

    An unreachable requirement is a valid answer, not an error: **200** with `status: INFEASIBLE`,
    `feasible: false`, an empty `plans` list and the reason per nutrient."""
    return services.run_optimizer(body)
