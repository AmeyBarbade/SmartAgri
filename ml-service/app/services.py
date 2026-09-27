"""Use cases behind the HTTP routes. Routes only parse, call one function here and return its result.

Prediction goes through ``YieldModel.predict`` (model_store) and optimization through ``optimizer.optimize``; this
module adds request-level checks (supported crop, model availability) and response metadata, never model or
optimizer mathematics.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path

from app.api.schemas import (
    ArtifactInfo,
    CropSupport,
    DatasetInfo,
    EvaluationInfo,
    HealthResponse,
    ModelHealth,
    ModelInfoResponse,
    OptimizeResponse,
    PredictYieldRequest,
    PredictYieldResponse,
    ScenarioPrediction,
    TrainingInfo,
)
from app.config import SERVICE_NAME, SERVICE_VERSION
from app.errors import ModelUnavailableError, OptimizationFailedError, UnsupportedCropError
from app.features import normalise_token
from app.model_store import METADATA_FILE, ModelLoadError, YieldModel
from app.optimizer import OptimizationRequest, OptimizerError, optimize

log = logging.getLogger("agrioptima.ml")

MODEL_DESCRIPTION = ("scikit-learn Pipeline: median/most-frequent imputation + one-hot encoding + regressor, "
                     "trained on district-grouped splits; one model for wheat and rice")
PREDICTION_DISCLAIMER = (
    "Observational model trained on farmer-reported yields. It ranks plans by association, is not a causal "
    "dose-response model and does not penalise over-application. Held-out-district RMSE: {rmse}."
)
OPTIMIZE_UNITS = {
    "requirement_kg_ha, supplied_kg_ha, excess_kg_ha, total_excess_kg_ha": "kg nutrient/ha (N, P2O5, K2O)",
    "requirement_field_kg, supplied_field_kg, excess_field_kg": "kg nutrient for the whole field",
    "items[].kg_ha, total_mass_kg_ha": "kg product/ha",
    "items[].field_kg, total_mass_field_kg": "kg product for the whole field",
    "cost_per_ha": "INR/ha",
    "field_cost": "INR for the whole field",
    "area_ha": "ha",
}


@dataclass
class ModelState:
    """The yield model loaded at startup, or why it could not be loaded."""

    artifact_dir: Path
    model: YieldModel | None = None
    error: ModelLoadError | None = None

    @classmethod
    def load(cls, artifact_dir: Path) -> "ModelState":
        try:
            model = YieldModel.load(artifact_dir)
            log.info("loaded yield model %s (sha256 %s)", model.version, model.sha256)
            return cls(artifact_dir, model=model)
        except ModelLoadError as exc:  # the service still starts: /health reports it, /optimize keeps working
            log.error("yield model not loaded: %s", exc)
            return cls(artifact_dir, error=exc)

    def require_model(self) -> YieldModel:
        if self.model is None:
            reason = self.error.public_reason if self.error else "model not loaded"
            raise ModelUnavailableError(f"The yield model is not available ({reason}).")
        return self.model

    def artifact_available(self) -> bool:
        meta = self.artifact_dir / METADATA_FILE
        if self.model is not None:
            return meta.is_file() and (self.artifact_dir / self.model.metadata["artifact"]["file"]).is_file()
        return meta.is_file()


def health(state: ModelState) -> HealthResponse:
    model = state.model
    return HealthResponse(
        status="UP" if model else "DEGRADED",
        service=SERVICE_NAME,
        version=SERVICE_VERSION,
        model=ModelHealth(
            status="UP" if model else "DOWN",
            artifact_available=state.artifact_available(),
            loaded=model is not None,
            model_version=model.version if model else None,
            error=None if model else (state.error.public_reason if state.error else "model not loaded"),
        ),
        optimizer="UP",
    )


def model_info(state: ModelState, crop: str | None = None) -> ModelInfoResponse:
    model = state.require_model()
    m = model.metadata
    selected = m["metrics"][m["algorithm"]]
    requested = None
    if crop is not None:
        name = normalise_token(crop) or crop
        requested = CropSupport(crop=name, supported=name in model.supported_crops)
    return ModelInfoResponse(
        model_version=model.version,
        model_type=m["algorithm"],
        model_description=MODEL_DESCRIPTION,
        problem=m["problem"],
        target=m["target"],
        supported_crops=model.supported_crops,
        requested_crop=requested,
        feature_version=model.feature_version,
        features=m["features"]["all"],
        scenario_features=m["features"]["scenario"],
        categories=m["features"]["categories"],
        supported_ranges=model.ranges,
        training=TrainingInfo(
            trained_at=m["trained_at"],
            data_label=m["data_label"],
            datasets=[DatasetInfo(**{k: d[k] for k in ("title", "handle", "version", "licence")})
                      for d in m["datasets"]],
            processed_data=m["processed_data"],
            split={k: v for k, v in m["split"].items() if k != "test_districts_list"},
            cross_validation=m["cross_validation"],
            random_seed=m["random_seed"],
            hyperparameters=m["hyperparameters"],
            training_code_commit=m.get("git", {}).get("commit"),
            training_environment=m["environment"],
        ),
        evaluation=EvaluationInfo(
            selection_metric=m["cross_validation"]["selection_metric"],
            cv=selected["cv"],
            test=selected["test"],
            test_by_crop=selected["test_by_crop"],
            baseline_crop_mean_test=m["metrics"]["baseline_crop_mean"]["test"],
            random_kfold_cv_for_comparison=m["selected_model_random_kfold_cv"],
        ),
        artifact=ArtifactInfo(sha256=model.sha256, bytes=m["artifact"]["bytes"]),
        limitations=_limitations(model),
    )


def _by_crop(model: YieldModel, metric: str, unit: str = "") -> str:
    by_crop = model.metadata["metrics"][model.metadata["algorithm"]]["test_by_crop"]
    return ", ".join(f"{crop.lower()} {by_crop[crop][metric]:g}{unit}" for crop in model.supported_crops)


def _limitations(model: YieldModel) -> list[str]:
    return [
        f"Supported crops: {', '.join(model.supported_crops)} only. Other crops (e.g. maize) have no training data "
        "and are rejected.",
        model.metadata["data_label"],
        "Associations, not causal fertilizer response: the model cannot penalise over-application.",
        "States outside categories.state are rejected.",
        "Numeric inputs outside supported_ranges are clipped to the nearest bound and flagged as extrapolation.",
        f"Accuracy on unseen districts, R2 within crop: {_by_crop(model, 'r2')}; "
        f"RMSE: {_by_crop(model, 'rmse', ' t/ha')}.",
    ]


def predict_yield(state: ModelState, request: PredictYieldRequest) -> PredictYieldResponse:
    model = state.require_model()
    unsupported = {i: s.crop for i, s in enumerate(request.scenarios) if s.crop not in model.supported_crops}
    if unsupported:
        raise UnsupportedCropError(
            f"The yield model supports {', '.join(model.supported_crops)} only; no prediction is made for "
            f"{', '.join(sorted(set(unsupported.values())))}.",
            errors={f"scenarios[{i}].crop": f"unsupported crop '{c}'" for i, c in unsupported.items()},
        )
    predictions = model.predict(request.scenarios)
    out, warnings = [], []
    for i, (scenario, p) in enumerate(zip(request.scenarios, predictions)):
        out.append(ScenarioPrediction(index=i, crop=scenario.crop, **p.model_dump()))
        for c in p.clipped_inputs:
            warnings.append(
                f"Scenario {i}: {c.feature} = {c.input_value:g} is outside the supported range for {scenario.crop} "
                f"[{c.supported_min:g}, {c.supported_max:g}]; the model used {c.used_value:g}, so this prediction "
                f"is an extrapolation and does not reflect the requested value."
            )
    return PredictYieldResponse(
        model_version=model.version,
        feature_version=model.feature_version,
        predictions=out,
        extrapolation=any(p.extrapolation for p in out),
        warnings=warnings,
        disclaimer=PREDICTION_DISCLAIMER.format(rmse=_by_crop(model, "rmse", " t/ha")),
    )


def run_optimizer(request: OptimizationRequest) -> OptimizeResponse:
    try:
        result = optimize(request)
    except OptimizerError as exc:
        log.error("optimizer failed: %s", exc)
        raise OptimizationFailedError("The optimizer could not produce a verified plan for this request.") from exc
    reason = None
    if result.status == "INFEASIBLE":
        reason = "; ".join(
            f"{s.nutrient}: requires {s.required_kg_ha:g} kg/ha but at most {s.max_supply_kg_ha:g} kg/ha can be "
            f"supplied ({s.reason})" for s in result.infeasibility
        )
    return OptimizeResponse(**dict(result), feasible=result.status != "INFEASIBLE",
                            infeasibility_reason=reason, units=OPTIMIZE_UNITS)
