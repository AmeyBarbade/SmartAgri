"""Loads the trained yield pipeline + metadata once and runs inference with range clipping."""

from __future__ import annotations

import hashlib
import json
import logging
import os
from pathlib import Path

import joblib
import sklearn
import xgboost

from app.features import FEATURES, NUMERIC_FEATURES, to_frame
from app.schemas import ClippedInput, YieldPrediction, YieldScenario

log = logging.getLogger(__name__)

DEFAULT_ARTIFACT_DIR = Path(__file__).resolve().parents[1] / "artifacts"
METADATA_FILE = "metadata.json"


def artifact_dir_from_env() -> Path:
    return Path(os.environ.get("MODEL_ARTIFACT_DIR") or DEFAULT_ARTIFACT_DIR)


class ModelLoadError(RuntimeError):
    """The artifact could not be loaded. ``str(exc)`` is for logs (may contain paths); ``public_reason`` is safe to
    return to API clients."""

    def __init__(self, message: str, public_reason: str):
        super().__init__(message)
        self.public_reason = public_reason


class YieldModel:
    def __init__(self, pipeline, metadata: dict, sha256: str | None = None):
        self.pipeline = pipeline
        self.metadata = metadata
        self.version: str = metadata["model_version"]
        self.ranges: dict[str, dict[str, list[float]]] = metadata["supported_ranges"]
        self.supported_crops: list[str] = list(metadata["supported_crops"])
        self.sha256 = sha256 or metadata["artifact"]["sha256"]
        # Fingerprint of the feature contract the artifact was trained with (names, groups, category levels).
        canonical = json.dumps(metadata["features"], sort_keys=True, separators=(",", ":"))
        self.feature_version = "features-sha256:" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]

    @classmethod
    def load(cls, artifact_dir: Path | None = None) -> "YieldModel":
        artifact_dir = Path(artifact_dir or artifact_dir_from_env())
        meta_path = artifact_dir / METADATA_FILE
        if not meta_path.exists():
            raise ModelLoadError(f"{meta_path} not found - run: python -m training.train",
                                 "model metadata not found")
        try:
            metadata = json.loads(meta_path.read_text(encoding="utf-8"))
            model_path = artifact_dir / metadata["artifact"]["file"]
            expected_sha = metadata["artifact"]["sha256"]
        except (OSError, ValueError, KeyError, TypeError) as exc:
            raise ModelLoadError(f"{meta_path}: unreadable metadata ({exc!r})", "model metadata is invalid") from exc
        if not model_path.is_file():
            raise ModelLoadError(f"{model_path} not found", "model artifact not found")
        digest = hashlib.sha256(model_path.read_bytes()).hexdigest()
        if digest != expected_sha:
            raise ModelLoadError(f"{model_path.name}: sha256 does not match metadata.json",
                                 "model artifact failed its sha256 integrity check")
        if metadata.get("features", {}).get("all") != FEATURES:
            raise ModelLoadError("artifact was trained with a different feature list than app/features.py",
                                 "model artifact does not match this service's feature contract")
        env = metadata.get("environment", {})
        for lib, installed in (("scikit-learn", sklearn.__version__), ("xgboost", xgboost.__version__)):
            if env.get(lib) != installed:
                log.warning("model trained with %s %s, running %s", lib, env.get(lib), installed)
        try:
            return cls(joblib.load(model_path), metadata, sha256=digest)
        except Exception as exc:  # corrupt pickle, missing metadata keys, incompatible library versions
            raise ModelLoadError(f"{model_path.name}: could not be deserialised ({exc!r})",
                                 "model artifact could not be deserialised") from exc

    def predict(self, scenarios: list[YieldScenario]) -> list[YieldPrediction]:
        records, clipped = [], []
        for scenario in scenarios:
            record = scenario.to_feature_record()
            limits = self.ranges[scenario.crop]
            out_of_range = []
            for col in NUMERIC_FEATURES:
                lo, hi = limits[col]
                value = min(max(record[col], lo), hi)
                if value != record[col]:
                    out_of_range.append(ClippedInput(feature=col, input_value=record[col], used_value=value,
                                                     supported_min=lo, supported_max=hi))
                    record[col] = value
            records.append(record)
            clipped.append(out_of_range)
        predictions = self.pipeline.predict(to_frame(records))
        return [
            YieldPrediction(predicted_yield_t_ha=round(max(float(p), 0.0), 3), extrapolation=bool(c),
                            clipped_features=[i.feature for i in c], clipped_inputs=c, model_version=self.version)
            for p, c in zip(predictions, clipped)
        ]
