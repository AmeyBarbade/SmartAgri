"""HTTP API (Milestone 6). Every test uses the committed model artifact and the real HiGHS optimizer; only failure
paths that cannot be produced with real inputs (solver failure, unexpected exception) are simulated."""

from __future__ import annotations

import hashlib
import json
import shutil
from datetime import date

import pytest
from fastapi.testclient import TestClient

from app import services
from app.api.schemas import HealthResponse, ModelInfoResponse, OptimizeResponse, PredictYieldResponse
from app.config import ConfigError, Settings, parse_origins
from app.features import to_frame
from app.main import create_app
from app.model_store import DEFAULT_ARTIFACT_DIR, ModelLoadError, YieldModel
from app.optimizer import OptimizerError, optimize
from app.schemas import YieldScenario
from tests.optimizer_contract import CONTRACT_CASES, V2_FERTILIZERS, only, request

PROBLEM = "application/problem+json"
EXTRA_OPTIMIZE_FIELDS = {"feasible", "infeasibility_reason", "units"}

WHEAT = {"crop": "WHEAT", "state": "BIHAR", "sowing_date": "2017-11-20", "soil_texture": "MEDIUM",
         "variety_type": "IMPROVED", "previous_crop": "RICE", "irrigation_available": True,
         "n_kg_ha": 120, "p2o5_kg_ha": 60, "k2o_kg_ha": 40}
RICE = {"crop": "RICE", "state": "WEST_BENGAL", "sowing_date": "2018-07-10", "irrigation_available": False,
        "n_kg_ha": 80, "p2o5_kg_ha": 40, "k2o_kg_ha": 20}


def settings(artifact_dir=DEFAULT_ARTIFACT_DIR, origins=("http://localhost:5173",)) -> Settings:
    return Settings(artifact_dir=artifact_dir, cors_allowed_origins=tuple(origins))


@pytest.fixture(scope="module")
def client():
    with TestClient(create_app(settings())) as c:
        yield c


@pytest.fixture(scope="module")
def model() -> YieldModel:
    return YieldModel.load()


def copy_artifact(tmp_path):
    for f in ("metadata.json", "yield_model.joblib"):
        shutil.copy(DEFAULT_ARTIFACT_DIR / f, tmp_path / f)
    return tmp_path


def assert_problem(response, status: int, kind: str) -> dict:
    assert response.status_code == status, response.text
    assert response.headers["content-type"] == PROBLEM
    body = response.json()
    assert body["type"] == f"urn:agrioptima:problem:{kind}" and body["status"] == status
    assert "Traceback" not in response.text and "File \"" not in response.text
    return body


def optimize_json(req) -> dict:
    return req.model_dump(mode="json")


# --- /health -----------------------------------------------------------------------------------------------------


def test_health_up(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = HealthResponse.model_validate(r.json())
    assert body.status == "UP" and body.optimizer == "UP"
    assert body.model.status == "UP" and body.model.loaded and body.model.artifact_available
    assert body.model.model_version == "yield-lds2018-xgboost-20260927" and body.model.error is None


def test_health_does_not_run_inference(client, monkeypatch):
    def boom(*_):
        raise AssertionError("health must not predict")
    monkeypatch.setattr(client.app.state.model_state.model.pipeline, "predict", boom)
    assert client.get("/health").json()["status"] == "UP"


def test_health_reports_artifact_removed_after_startup(tmp_path):
    with TestClient(create_app(settings(copy_artifact(tmp_path)))) as c:
        assert c.get("/health").json()["model"]["artifact_available"] is True
        (tmp_path / "yield_model.joblib").unlink()
        body = c.get("/health").json()
        assert body["model"]["artifact_available"] is False
        assert body["model"]["loaded"] is True  # loaded in memory at startup, still serving


# --- model loading behaviour -------------------------------------------------------------------------------------


def test_missing_artifact_degrades_but_optimizer_still_works(tmp_path):
    with TestClient(create_app(settings(tmp_path))) as c:
        r = c.get("/health")
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "DEGRADED" and body["optimizer"] == "UP"
        assert body["model"] == {"status": "DOWN", "artifact_available": False, "loaded": False,
                                 "model_version": None, "error": "model metadata not found"}
        assert str(tmp_path) not in r.text
        info = assert_problem(c.get("/model/info"), 503, "model-unavailable")
        pred = assert_problem(c.post("/predict-yield", json={"scenarios": [WHEAT]}), 503, "model-unavailable")
        for p in (info, pred):
            assert str(tmp_path) not in json.dumps(p) and "metadata.json" not in json.dumps(p)
        assert c.post("/optimize", json=optimize_json(request(40, 0, 0))).json()["status"] == "OPTIMAL"


def _tamper_sha(d):
    meta = json.loads((d / "metadata.json").read_text(encoding="utf-8"))
    meta["artifact"]["sha256"] = "0" * 64
    (d / "metadata.json").write_text(json.dumps(meta), encoding="utf-8")


def _corrupt_pickle(d):
    blob = b"not a joblib file"
    (d / "yield_model.joblib").write_bytes(blob)
    meta = json.loads((d / "metadata.json").read_text(encoding="utf-8"))
    meta["artifact"]["sha256"] = hashlib.sha256(blob).hexdigest()
    (d / "metadata.json").write_text(json.dumps(meta), encoding="utf-8")


def _feature_drift(d):
    meta = json.loads((d / "metadata.json").read_text(encoding="utf-8"))
    meta["features"]["all"] = meta["features"]["all"][:-1]
    (d / "metadata.json").write_text(json.dumps(meta), encoding="utf-8")


@pytest.mark.parametrize("damage, reason", [
    (lambda d: (d / "yield_model.joblib").unlink(), "model artifact not found"),
    (lambda d: (d / "metadata.json").write_text("{not json", encoding="utf-8"), "model metadata is invalid"),
    (_tamper_sha, "model artifact failed its sha256 integrity check"),
    (_corrupt_pickle, "model artifact could not be deserialised"),
    (_feature_drift, "model artifact does not match this service's feature contract"),
])
def test_broken_artifact_is_refused_with_a_safe_reason(tmp_path, damage, reason):
    damage(copy_artifact(tmp_path))
    with pytest.raises(ModelLoadError) as exc:
        YieldModel.load(tmp_path)
    assert exc.value.public_reason == reason
    with TestClient(create_app(settings(tmp_path))) as c:
        r = c.get("/health")
        assert r.json()["status"] == "DEGRADED" and r.json()["model"]["error"] == reason
        assert str(tmp_path) not in r.text
        body = assert_problem(c.post("/predict-yield", json={"scenarios": [WHEAT]}), 503, "model-unavailable")
        assert reason in body["detail"]


def test_model_is_loaded_once_at_startup(tmp_path, monkeypatch):
    calls = []
    real = YieldModel.load.__func__
    monkeypatch.setattr(YieldModel, "load", classmethod(lambda cls, d=None: calls.append(d) or real(cls, d)))
    with TestClient(create_app(settings())) as c:
        for _ in range(3):
            c.get("/health")
            c.post("/predict-yield", json={"scenarios": [WHEAT]})
    assert len(calls) == 1


# --- /model/info -------------------------------------------------------------------------------------------------


def test_model_info(client, model):
    r = client.get("/model/info")
    assert r.status_code == 200
    body = ModelInfoResponse.model_validate(r.json())
    assert body.model_version == model.version and body.model_type == "xgboost"
    assert body.supported_crops == ["WHEAT", "RICE"] and body.requested_crop is None
    assert body.artifact.sha256 == hashlib.sha256((DEFAULT_ARTIFACT_DIR / "yield_model.joblib").read_bytes()).hexdigest()
    assert body.feature_version == model.feature_version and body.feature_version.startswith("features-sha256:")
    assert body.target["unit"] == "t/ha"
    assert [d.handle for d in body.training.datasets] == ["hdl:11529/10548507", "hdl:11529/10548656"]
    assert body.training.processed_data["rows"] == 15399
    assert body.evaluation.test_by_crop["WHEAT"]["rmse"] == 0.7133
    assert body.evaluation.cv["rmse_mean"] == 1.022
    assert "requested_crop" not in r.json()
    # no filesystem internals
    for needle in (str(DEFAULT_ARTIFACT_DIR), "artifacts", ".joblib", "\\\\", "/home", "C:"):
        assert needle not in r.text, needle


@pytest.mark.parametrize("crop, name, supported", [
    ("MAIZE", "MAIZE", False), ("maize", "MAIZE", False), ("wheat", "WHEAT", True), ("Rice", "RICE", True),
])
def test_model_info_reports_crop_support(client, crop, name, supported):
    assert client.get("/model/info", params={"crop": crop}).json()["requested_crop"] == {"crop": name,
                                                                                         "supported": supported}


# --- /predict-yield ----------------------------------------------------------------------------------------------


def test_predict_yield_batch(client, model):
    r = client.post("/predict-yield", json={"scenarios": [WHEAT, RICE, {**WHEAT, "n_kg_ha": 30}]})
    assert r.status_code == 200
    body = PredictYieldResponse.model_validate(r.json())
    assert body.unit == "t/ha" and body.model_version == model.version and not body.extrapolation
    assert [p.index for p in body.predictions] == [0, 1, 2]
    assert [p.crop for p in body.predictions] == ["WHEAT", "RICE", "WHEAT"]
    for p in body.predictions:
        assert 0.3 < p.predicted_yield_t_ha < 10 and not p.clipped_inputs
    assert body.predictions[0].predicted_yield_t_ha != body.predictions[2].predicted_yield_t_ha
    assert body.warnings == [] and "farmer-reported" in body.disclaimer


def test_predict_yield_equals_model_store_and_training_preprocessing(client, model):
    """The route adds nothing to the number: it is YieldModel.predict, which is the training pipeline."""
    api = client.post("/predict-yield", json={"scenarios": [WHEAT, RICE]}).json()["predictions"]
    scenarios = [YieldScenario(**WHEAT), YieldScenario(**RICE)]
    direct = model.predict(scenarios)
    raw = model.pipeline.predict(to_frame([s.to_feature_record() for s in scenarios]))
    for a, d, r in zip(api, direct, raw):
        assert a["predicted_yield_t_ha"] == d.predicted_yield_t_ha == round(max(float(r), 0), 3)


def test_predict_yield_normalises_like_the_model_schema(client):
    loose = {**WHEAT, "crop": "wheat", "state": "Bihar", "previous_crop": "Lentil", "soil_texture": "medium"}
    strict = {**WHEAT, "previous_crop": "PULSE"}
    a, b = (client.post("/predict-yield", json={"scenarios": [s]}).json()["predictions"][0] for s in (loose, strict))
    assert a["predicted_yield_t_ha"] == b["predicted_yield_t_ha"] and a["crop"] == "WHEAT"


def test_predict_yield_is_deterministic(client):
    first, second = (client.post("/predict-yield", json={"scenarios": [WHEAT, RICE]}).json() for _ in range(2))
    assert first == second


def test_extrapolation_is_clipped_and_reported(client, model):
    hi_n = model.ranges["WHEAT"]["n_kg_ha"][1]
    r = client.post("/predict-yield", json={"scenarios": [{**WHEAT, "n_kg_ha": 900}, {**WHEAT, "n_kg_ha": hi_n}]})
    far, edge = r.json()["predictions"]
    assert r.json()["extrapolation"] is True
    assert far["extrapolation"] and far["clipped_features"] == ["n_kg_ha"]
    assert far["clipped_inputs"] == [{"feature": "n_kg_ha", "input_value": 900.0, "used_value": hi_n,
                                      "supported_min": model.ranges["WHEAT"]["n_kg_ha"][0], "supported_max": hi_n}]
    assert far["predicted_yield_t_ha"] == edge["predicted_yield_t_ha"]
    assert not edge["extrapolation"]
    assert len(r.json()["warnings"]) == 1 and "Scenario 0: n_kg_ha = 900" in r.json()["warnings"][0]


def test_late_sowing_date_is_clipped_as_sowing_day(client, model):
    lo, hi = model.ranges["WHEAT"]["sowing_day"]
    p = client.post("/predict-yield", json={"scenarios": [{**WHEAT, "sowing_date": "2018-02-15"}]}).json()
    clipped = p["predictions"][0]["clipped_inputs"]
    assert [c["feature"] for c in clipped] == ["sowing_day"]
    assert clipped[0]["input_value"] == (date(2018, 2, 15) - date(2017, 10, 1)).days > hi
    assert clipped[0]["used_value"] == hi


def test_unsupported_crop_is_rejected(client):
    body = assert_problem(client.post("/predict-yield", json={"scenarios": [{**WHEAT, "crop": "MAIZE"}]}),
                          422, "unsupported-crop")
    assert "MAIZE" in body["detail"] and body["errors"] == {"scenarios[0].crop": "unsupported crop 'MAIZE'"}


def test_one_unsupported_crop_rejects_the_whole_batch(client):
    body = assert_problem(client.post("/predict-yield", json={"scenarios": [WHEAT, {**RICE, "crop": "maize"},
                                                                             {**WHEAT, "crop": "Cotton"}]}),
                          422, "unsupported-crop")
    assert set(body["errors"]) == {"scenarios[1].crop", "scenarios[2].crop"}
    assert "predictions" not in body


@pytest.mark.parametrize("change, field", [
    ({"n_kg_ha": -1}, "scenarios[0].n_kg_ha"),
    ({"k2o_kg_ha": 1000.5}, "scenarios[0].k2o_kg_ha"),
    ({"state": "Kerala"}, "scenarios[0].state"),
    ({"soil_texture": "ROCKY"}, "scenarios[0].soil_texture"),
    ({"variety_type": "GMO"}, "scenarios[0].variety_type"),
    ({"sowing_date": "2017-13-40"}, "scenarios[0].sowing_date"),
    ({"irrigation_available": "sometimes"}, "scenarios[0].irrigation_available"),
    ({"n_kg_ha": "lots"}, "scenarios[0].n_kg_ha"),
    ({"yield_t_ha": 3.0}, "scenarios[0].yield_t_ha"),
    ({"crop": ""}, "scenarios[0].crop"),
])
def test_invalid_prediction_input(client, change, field):
    body = assert_problem(client.post("/predict-yield", json={"scenarios": [{**WHEAT, **change}]}), 422, "validation")
    assert field in body["errors"]


def test_missing_prediction_fields(client):
    scenario = {k: v for k, v in WHEAT.items() if k not in ("state", "n_kg_ha")}
    body = assert_problem(client.post("/predict-yield", json={"scenarios": [scenario]}), 422, "validation")
    assert {"scenarios[0].state", "scenarios[0].n_kg_ha"} <= body["errors"].keys()


@pytest.mark.parametrize("payload, field", [
    ({"scenarios": []}, "scenarios"),
    ({"scenarios": [WHEAT] * 51}, "scenarios"),
    ({}, "scenarios"),
    ({"scenarios": [WHEAT], "debug": True}, "debug"),
    ([WHEAT], "body"),
])
def test_invalid_prediction_batch(client, payload, field):
    assert field in assert_problem(client.post("/predict-yield", json=payload), 422, "validation")["errors"]


@pytest.mark.parametrize("value", ["NaN", "Infinity", "-Infinity"])
def test_non_finite_doses_are_rejected(client, value):
    raw = json.dumps({"scenarios": [WHEAT]}).replace('"n_kg_ha": 120', f'"n_kg_ha": {value}')
    r = client.post("/predict-yield", content=raw, headers={"content-type": "application/json"})
    assert "scenarios[0].n_kg_ha" in assert_problem(r, 422, "validation")["errors"]


def test_malformed_json(client):
    for path in ("/predict-yield", "/optimize"):
        r = client.post(path, content=b'{"scenarios": [', headers={"content-type": "application/json"})
        assert assert_problem(r, 400, "malformed-request")["detail"] == "The request body is not valid JSON."


def test_validation_errors_do_not_echo_input(client):
    r = client.post("/predict-yield", json={"scenarios": [{**WHEAT, "state": "<script>secret-token</script>"}]})
    assert "secret-token" not in assert_problem(r, 422, "validation").__repr__()


# --- /optimize ---------------------------------------------------------------------------------------------------


def test_optimize_returns_the_optimizer_result_unchanged(client):
    req = request(0, 60, 40, area_ha=1.0)
    r = client.post("/optimize", json=optimize_json(req))
    assert r.status_code == 200
    body = OptimizeResponse.model_validate(r.json())
    assert body.status == "OPTIMAL" and body.feasible and body.infeasibility_reason is None
    assert [p.strategy.value for p in body.plans] == ["LOWEST_COST", "MIN_EXCESS", "BALANCED"]
    assert all(p.feasible for p in body.plans)
    plans = {p.strategy.value: {i.code: i.kg_ha for i in p.items} for p in body.plans}
    assert plans["LOWEST_COST"] == {"DAP": 43.479, "NPK_10_26_26": 153.847}
    assert plans["MIN_EXCESS"] == {"MOP": 66.667, "SSP": 375.0}
    assert body.plans[0].cost_per_ha == pytest.approx(5697.03, abs=0.01)
    assert body.plans[0].excess_kg_ha.n == pytest.approx(23.21, abs=0.01)
    assert body.units["cost_per_ha"] == "INR/ha" and body.units["field_cost"] == "INR for the whole field"
    assert {k: v for k, v in r.json().items() if k not in EXTRA_OPTIMIZE_FIELDS} == optimize(req).model_dump(mode="json")


@pytest.mark.parametrize("name", list(CONTRACT_CASES))
def test_optimize_matches_the_contract_cases(client, name):
    """Same numbers as the optimizer function (and hence the backend contract fixture) for every contract case."""
    req = CONTRACT_CASES[name]
    body = client.post("/optimize", json=optimize_json(req)).json()
    assert {k: v for k, v in body.items() if k not in EXTRA_OPTIMIZE_FIELDS} == optimize(req).model_dump(mode="json")
    assert body["feasible"] is (body["status"] != "INFEASIBLE")


def test_optimize_field_totals(client):
    body = client.post("/optimize", json=optimize_json(request(67.5, 45, 22.5, area_ha=0.5))).json()
    plan = body["plans"][0]
    assert plan["field_cost"] == pytest.approx(plan["cost_per_ha"] * 0.5)
    assert plan["total_mass_field_kg"] == pytest.approx(plan["total_mass_kg_ha"] * 0.5)
    assert body["requirement_field_kg"] == {"n": 33.75, "p2o5": 22.5, "k2o": 11.25}
    for item in plan["items"]:
        assert item["field_kg"] == pytest.approx(item["kg_ha"] * 0.5)


def test_infeasible_optimization(client):
    r = client.post("/optimize", json=optimize_json(request(40, 30, 20, fertilizers=only("UREA", "DAP"))))
    assert r.status_code == 200
    body = OptimizeResponse.model_validate(r.json())
    assert body.status == "INFEASIBLE" and not body.feasible and body.plans == []
    assert [s.nutrient for s in body.infeasibility] == ["K2O"]
    assert body.infeasibility_reason == ("K2O: requires 20 kg/ha but at most 0 kg/ha can be supplied "
                                         "(no available fertilizer contains K2O)")


def test_infeasible_because_of_caps(client):
    ferts = only("UREA", UREA={"max_kg_ha": 50})
    body = client.post("/optimize", json=optimize_json(request(40, 0, 0, fertilizers=ferts))).json()
    assert not body["feasible"] and "UREA <= 50 kg/ha" in body["infeasibility_reason"]


def test_no_fertilizers_and_nothing_required(client):
    none = client.post("/optimize", json=optimize_json(request(10, 0, 0, fertilizers=[]))).json()
    assert not none["feasible"] and "no fertilizers are available" in none["infeasibility_reason"]
    zero = client.post("/optimize", json=optimize_json(request(0, 0, 0))).json()
    assert zero["status"] == "NOTHING_REQUIRED" and zero["feasible"] and len(zero["plans"]) == 3


def _body(**changes):
    base = {"requirement_kg_ha": {"n": 40, "p2o5": 20, "k2o": 10}, "area_ha": 1.0, "fertilizers": V2_FERTILIZERS}
    return {**base, **changes}


@pytest.mark.parametrize("payload, field", [
    (_body(requirement_kg_ha={"n": -1, "p2o5": 0, "k2o": 0}), "requirement_kg_ha.n"),
    (_body(requirement_kg_ha={"n": 1001, "p2o5": 0, "k2o": 0}), "requirement_kg_ha.n"),
    (_body(requirement_kg_ha={"n": 40, "p2o5": 20}), "requirement_kg_ha.k2o"),
    (_body(requirement_kg_ha={"n": 40, "p2o5": 20, "k2o": 10, "s": 5}), "requirement_kg_ha.s"),
    (_body(requirement_kg_ha="lots"), "requirement_kg_ha"),
    (_body(area_ha=0), "area_ha"),
    (_body(area_ha=-2), "area_ha"),
    (_body(area_ha="two"), "area_ha"),
    ({k: v for k, v in _body().items() if k != "requirement_kg_ha"}, "requirement_kg_ha"),
    ({k: v for k, v in _body().items() if k != "area_ha"}, "area_ha"),
    (_body(fertilizers=[{**V2_FERTILIZERS[0]}, {**V2_FERTILIZERS[0]}]), "fertilizers"),
    (_body(fertilizers=[{**V2_FERTILIZERS[0], "n_pct": 146}]), "fertilizers[0].n_pct"),
    (_body(fertilizers=[{**V2_FERTILIZERS[1], "k2o_pct": 50}]), "fertilizers[0]"),
    (_body(fertilizers=[{**V2_FERTILIZERS[0], "price_per_kg": -5}]), "fertilizers[0].price_per_kg"),
    (_body(fertilizers=[{**V2_FERTILIZERS[0], "max_kg_ha": -1}]), "fertilizers[0].max_kg_ha"),
    (_body(fertilizers=[{**V2_FERTILIZERS[0], "code": ""}]), "fertilizers[0].code"),
    (_body(fertilizers=[{k: v for k, v in V2_FERTILIZERS[0].items() if k != "n_pct"}]), "fertilizers[0].n_pct"),
    (_body(fertilizers=[{**V2_FERTILIZERS[0], "density": 1}]), "fertilizers[0].density"),
    (_body(fertilizers=V2_FERTILIZERS * 11), "fertilizers"),
    (_body(parameters={"balanced_budget_share": 2}), "parameters.balanced_budget_share"),
    (_body(parameters={"default_max_kg_ha": 0}), "parameters.default_max_kg_ha"),
    (_body(strategy="CHEAPEST"), "strategy"),
])
def test_malformed_optimizer_request(client, payload, field):
    body = assert_problem(client.post("/optimize", json=payload), 422, "validation")
    assert field in body["errors"], body["errors"]


def test_non_finite_optimizer_values_are_rejected(client):
    raw = json.dumps(_body()).replace('"price_per_kg": 5.92', '"price_per_kg": NaN')
    r = client.post("/optimize", content=raw, headers={"content-type": "application/json"})
    assert "fertilizers[0].price_per_kg" in assert_problem(r, 422, "validation")["errors"]


def test_optimizer_failure_is_a_clean_500(client, monkeypatch):
    def fail(_):
        raise OptimizerError("HiGHS did not find an optimum: internal detail C:\\secret")
    monkeypatch.setattr(services, "optimize", fail)
    body = assert_problem(client.post("/optimize", json=_body()), 500, "optimization-failed")
    assert "secret" not in json.dumps(body) and "HiGHS" not in json.dumps(body)


def test_unexpected_error_is_a_clean_500(monkeypatch):
    def crash(_):
        raise RuntimeError("boom at C:\\Users\\secret\\file.py")
    monkeypatch.setattr(services, "run_optimizer", crash)
    with TestClient(create_app(settings()), raise_server_exceptions=False) as c:
        r = c.post("/optimize", json=_body())
    body = assert_problem(r, 500, "internal")
    assert body["detail"] == "An unexpected error occurred." and "secret" not in r.text and "boom" not in r.text


# --- routing, CORS, OpenAPI --------------------------------------------------------------------------------------


def test_unknown_route_and_wrong_method(client):
    assert_problem(client.get("/predict"), 404, "not-found")
    assert_problem(client.get("/optimize"), 405, "method-not-allowed")


def test_cors_allows_only_configured_origins(client):
    ok = client.options("/optimize", headers={"Origin": "http://localhost:5173", "Access-Control-Request-Method": "POST"})
    assert ok.headers.get("access-control-allow-origin") == "http://localhost:5173"
    assert "access-control-allow-credentials" not in ok.headers
    bad = client.options("/optimize", headers={"Origin": "http://evil.example", "Access-Control-Request-Method": "POST"})
    assert "access-control-allow-origin" not in bad.headers
    get = client.get("/health", headers={"Origin": "http://evil.example"})
    assert "access-control-allow-origin" not in get.headers


def test_cors_can_be_disabled():
    with TestClient(create_app(settings(origins=()))) as c:
        r = c.options("/optimize", headers={"Origin": "http://localhost:5173", "Access-Control-Request-Method": "POST"})
        assert "access-control-allow-origin" not in r.headers


def test_cors_origin_parsing(monkeypatch):
    assert parse_origins(" http://localhost:5173/ , https://app.example.org ") == ("http://localhost:5173",
                                                                                 "https://app.example.org")
    assert parse_origins("") == ()
    for bad in ("*", "http://*.example.org", "localhost:5173"):
        with pytest.raises(ConfigError):
            parse_origins(bad)
    monkeypatch.setenv("ML_CORS_ALLOWED_ORIGINS", "http://a.test,http://b.test")
    monkeypatch.setenv("MODEL_ARTIFACT_DIR", "/models")
    s = Settings.from_env()
    assert s.cors_allowed_origins == ("http://a.test", "http://b.test") and s.artifact_dir.as_posix() == "/models"


def test_openapi_documents_endpoints_units_and_errors(client):
    doc = client.get("/openapi.json").json()
    paths = doc["paths"]
    assert set(paths) == {"/health", "/model/info", "/predict-yield", "/optimize"}
    assert {"200", "400", "422", "500", "503"} <= set(paths["/predict-yield"]["post"]["responses"])
    assert {"200", "400", "422", "500"} <= set(paths["/optimize"]["post"]["responses"])
    assert "503" in paths["/model/info"]["get"]["responses"]
    examples = paths["/optimize"]["post"]["requestBody"]["content"]["application/json"]["examples"]
    assert {"pk_only", "rice", "infeasible"} <= set(examples)
    schemas = doc["components"]["schemas"]
    assert "INR/ha" in schemas["FertilizerPlan"]["properties"]["cost_per_ha"]["description"]
    assert "kg product/ha" in schemas["PlanItem"]["properties"]["kg_ha"]["description"]
    assert "t/ha" in schemas["ScenarioPrediction"]["properties"]["predicted_yield_t_ha"]["description"]
    assert "kg/ha" in schemas["PredictionScenario"]["properties"]["n_kg_ha"]["description"]
    assert schemas["PredictionScenario"]["properties"]["n_kg_ha"]["minimum"] == 0
    assert "Problem" in schemas


def test_openapi_examples_are_valid_requests(client):
    """The Swagger examples actually work (and the maize/infeasible ones behave as their summaries say)."""
    doc = client.get("/openapi.json").json()["paths"]
    for path in ("/predict-yield", "/optimize"):
        for name, ex in doc[path]["post"]["requestBody"]["content"]["application/json"]["examples"].items():
            r = client.post(path, json=ex["value"])
            expected = 422 if name == "maize" else 200
            assert r.status_code == expected, (path, name, r.text)
