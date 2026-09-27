"""HTTP request/response contracts of the ML service.

Units: yield t/ha; nutrient requirements and supply kg/ha (N, P2O5, K2O, oxide basis); fertilizer quantities kg
product/ha; ``*_field_kg`` and ``field_cost`` are totals for the whole field; costs in INR (INR/ha and INR per field).
The optimizer request/response are the optimizer's own models (app/optimizer.py), which are also the contract the
backend's ``FertilizerPlanVerifier`` is tested against.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from app.optimizer import OptimizationResult
from app.schemas import YieldPrediction, YieldScenario

MAX_SCENARIOS = 50

# --- /health -----------------------------------------------------------------------------------------------------


class ModelHealth(BaseModel):
    status: Literal["UP", "DOWN"]
    artifact_available: bool = Field(description="The artifact files are present now (cheap check, no inference)")
    loaded: bool = Field(description="The artifact passed its sha256/feature-contract checks and was loaded at startup")
    model_version: str | None
    error: str | None = Field(description="Why the model is not loaded (no file paths)")


class HealthResponse(BaseModel):
    status: Literal["UP", "DEGRADED"] = Field(
        description="UP: everything available. DEGRADED: running, /optimize works, but the yield model is not loaded")
    service: str
    version: str
    model: ModelHealth
    optimizer: Literal["UP"] = Field(description="The optimizer has no external dependencies")


# --- /model/info -------------------------------------------------------------------------------------------------


class CropSupport(BaseModel):
    crop: str
    supported: bool


class DatasetInfo(BaseModel):
    title: str
    handle: str
    version: str
    licence: str


class TrainingInfo(BaseModel):
    trained_at: str
    data_label: str
    datasets: list[DatasetInfo]
    processed_data: dict = Field(description="Cleaned training table: file name, md5 and row count")
    split: dict = Field(description="Train/test split method and sizes (district-grouped)")
    cross_validation: dict
    random_seed: int
    hyperparameters: dict
    training_code_commit: str | None
    training_environment: dict = Field(description="Library versions the artifact was trained with")


class EvaluationInfo(BaseModel):
    selection_metric: str
    cv: dict = Field(description="Grouped 5-fold CV on the training split (RMSE/MAE in t/ha)")
    test: dict = Field(description="Held-out districts (MAE/RMSE in t/ha, R2)")
    test_by_crop: dict
    baseline_crop_mean_test: dict
    random_kfold_cv_for_comparison: dict = Field(description="Optimistic random split, shown only to quantify leakage")


class ArtifactInfo(BaseModel):
    sha256: str = Field(description="Checksum of the loaded model file, verified against metadata at startup")
    bytes: int


class ModelInfoResponse(BaseModel):
    model_version: str
    model_type: str = Field(description="Selected algorithm")
    model_description: str
    problem: str
    target: dict = Field(description="Target name, unit (t/ha) and definition")
    supported_crops: list[str]
    requested_crop: CropSupport | None = Field(default=None, description="Present when ?crop= was given")
    feature_version: str = Field(description="Fingerprint of the feature contract recorded in the artifact")
    features: list[str]
    scenario_features: list[str] = Field(description="Features that describe the fertilizer plan")
    categories: dict[str, list[str]] = Field(description="Accepted category values per categorical feature")
    supported_ranges: dict[str, dict[str, list[float]]] = Field(
        description="Per crop: [min, max] seen in training. Inputs outside are clipped and flagged as extrapolation. "
                    "n/p2o5/k2o in kg/ha, sowing_day in days after 1 Oct (wheat) / 1 May (rice)")
    training: TrainingInfo
    evaluation: EvaluationInfo
    artifact: ArtifactInfo
    limitations: list[str]


# --- /predict-yield ----------------------------------------------------------------------------------------------


class PredictionScenario(YieldScenario):
    """One field + one candidate fertilizer scenario (all values known before fertilizer is applied).

    ``crop`` is accepted as any name here so that crops the model was not trained on (e.g. MAIZE) are answered with
    an explicit ``unsupported-crop`` error instead of a generic validation message.
    """

    crop: str = Field(min_length=1, max_length=40, description="WHEAT or RICE (see /model/info supported_crops)")


class PredictYieldRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    scenarios: list[PredictionScenario] = Field(
        min_length=1, max_length=MAX_SCENARIOS,
        description="Batch: e.g. one scenario per candidate plan plus current practice. Answers keep this order")


class ScenarioPrediction(YieldPrediction):
    index: int = Field(description="Position of the scenario in the request")
    crop: str


class PredictYieldResponse(BaseModel):
    model_version: str
    feature_version: str
    unit: Literal["t/ha"] = "t/ha"
    predictions: list[ScenarioPrediction]
    extrapolation: bool = Field(description="True if any scenario had a clipped input")
    warnings: list[str] = Field(description="One line per clipped input, stating the value the model used")
    disclaimer: str


# --- /optimize ---------------------------------------------------------------------------------------------------


class OptimizeResponse(OptimizationResult):
    feasible: bool = Field(description="False only when status is INFEASIBLE (then plans is empty)")
    infeasibility_reason: str | None = Field(description="Human-readable summary of `infeasibility`, else null")
    units: dict[str, str] = Field(description="Units of the numeric fields")
