"""Tests against the committed model artifact (artifacts/yield_model.joblib + metadata.json)."""

from datetime import date

import pytest
from pydantic import ValidationError

from app.features import FEATURES, to_frame
from app.model_store import YieldModel
from app.schemas import YieldScenario


@pytest.fixture(scope="module")
def model() -> YieldModel:
    return YieldModel.load()


def scenario(**overrides) -> YieldScenario:
    data = dict(crop="WHEAT", state="Bihar", sowing_date=date(2017, 11, 20), soil_texture="MEDIUM",
                variety_type="IMPROVED", previous_crop="Rice", irrigation_available=True,
                n_kg_ha=120, p2o5_kg_ha=60, k2o_kg_ha=40)
    data.update(overrides)
    return YieldScenario(**data)


def test_metadata_matches_feature_contract(model):
    assert model.metadata["features"]["all"] == FEATURES
    assert "Real public survey data" in model.metadata["data_label"]
    assert model.metadata["algorithm"] in ("linear_regression", "decision_tree", "random_forest", "xgboost")
    for name in ("linear_regression", "decision_tree", "random_forest", "xgboost"):
        assert {"mae", "rmse", "r2"} <= model.metadata["metrics"][name]["test"].keys()


def test_prediction_is_plausible_and_batched(model):
    out = model.predict([scenario(), scenario(crop="RICE", state="West Bengal", sowing_date=date(2018, 7, 10))])
    assert len(out) == 2
    for p in out:
        assert 0.3 < p.predicted_yield_t_ha < 10
        assert p.model_version == model.version
        assert not p.extrapolation


def test_inference_uses_the_same_preprocessing_as_training(model):
    s = scenario()
    direct = float(model.pipeline.predict(to_frame([s.to_feature_record()]))[0])
    assert model.predict([s])[0].predicted_yield_t_ha == pytest.approx(max(direct, 0), abs=1e-3)


def test_model_distinguishes_fertilizer_plans(model):
    low, high = model.predict([scenario(n_kg_ha=0, p2o5_kg_ha=0, k2o_kg_ha=0), scenario()])
    assert low.predicted_yield_t_ha != high.predicted_yield_t_ha


def test_out_of_range_inputs_are_clipped_and_flagged(model):
    hi_n = model.ranges["WHEAT"]["n_kg_ha"][1]
    far, edge = model.predict([scenario(n_kg_ha=900), scenario(n_kg_ha=hi_n)])
    assert far.extrapolation and far.clipped_features == ["n_kg_ha"]
    assert far.predicted_yield_t_ha == edge.predicted_yield_t_ha


def test_prediction_is_deterministic(model):
    assert model.predict([scenario()]) == model.predict([scenario()])


@pytest.mark.parametrize("bad", [
    {"crop": "MAIZE"}, {"state": "Kerala"}, {"n_kg_ha": -1}, {"soil_texture": "ROCKY"}, {"yield_t_ha": 3.0},
])
def test_invalid_scenarios_are_rejected(bad):
    with pytest.raises(ValidationError):
        scenario(**bad)


def test_tampered_artifact_is_refused(tmp_path):
    import json
    import shutil
    from app.model_store import DEFAULT_ARTIFACT_DIR, ModelLoadError
    for f in ("metadata.json", "yield_model.joblib"):
        shutil.copy(DEFAULT_ARTIFACT_DIR / f, tmp_path / f)
    meta = json.loads((tmp_path / "metadata.json").read_text(encoding="utf-8"))
    meta["artifact"]["sha256"] = "0" * 64
    (tmp_path / "metadata.json").write_text(json.dumps(meta), encoding="utf-8")
    with pytest.raises(ModelLoadError, match="sha256"):
        YieldModel.load(tmp_path)
