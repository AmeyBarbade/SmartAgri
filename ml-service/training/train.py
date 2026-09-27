"""Train, compare and select the crop-yield regression model.

Usage (from ml-service/):  python -m training.train
Reads data/processed/lds_wheat_rice_2018.csv (run training.prepare_data first) and writes
artifacts/yield_model.joblib, artifacts/metadata.json, reports/model_comparison.csv, reports/evaluation_report.md

Methodology
- Test set: ~20 % of *districts* per crop are held out entirely (GroupShuffleSplit), so the test score
  measures generalisation to locations the model has never seen, not memorisation of neighbouring farms.
- Model selection: 5-fold GroupKFold (grouped by district) cross-validation on the training set only;
  the model with the lowest mean CV RMSE is selected. The test set is used once, for reporting.
- The saved artifact is the selected pipeline fitted on the training split, so the reported test metrics
  describe exactly the artifact that is served.
"""

from __future__ import annotations

import hashlib
import json
import platform
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
import xgboost
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import GridSearchCV, GroupKFold, GroupShuffleSplit, KFold, cross_val_score
from sklearn.pipeline import Pipeline
from sklearn.tree import DecisionTreeRegressor
from sklearn.ensemble import RandomForestRegressor
from xgboost import XGBRegressor

from app.features import (
    BINARY_FEATURES, CATEGORICAL_FEATURES, CATEGORIES, FEATURES, LEAKAGE_EXCLUDED, NUMERIC_FEATURES,
    SCENARIO_BINARY, SCENARIO_NUMERIC, SUPPORTED_CROPS, TARGET, build_preprocessor,
)
from training.datasets import DATASETS, PROCESSED_FILE
from training.download_data import md5_of

SEED = 42
TEST_FRACTION = 0.2
CV_FOLDS = 5
ML_ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS_DIR = ML_ROOT / "artifacts"
REPORTS_DIR = ML_ROOT / "reports"
DATA_LABEL = ("Real public survey data (CIMMYT CSISA Landscape Diagnostic Survey 2018, farmer-reported yield). "
              "Observational, not a controlled fertilizer trial.")

CANDIDATES = {
    "linear_regression": (LinearRegression(), {}),
    "decision_tree": (
        DecisionTreeRegressor(random_state=SEED),
        {"max_depth": [4, 6, 8, 10], "min_samples_leaf": [20, 50, 100]},
    ),
    "random_forest": (
        RandomForestRegressor(n_estimators=300, random_state=SEED, n_jobs=-1),
        {"max_depth": [10, 16], "min_samples_leaf": [10, 30], "max_features": [0.33, 0.6]},
    ),
    "xgboost": (
        XGBRegressor(random_state=SEED, n_jobs=-1, subsample=0.8, colsample_bytree=0.8, tree_method="hist"),
        {"n_estimators": [300, 600], "max_depth": [3, 5], "learning_rate": [0.03, 0.06],
         "min_child_weight": [5, 20]},
    ),
}


def metrics(y_true, y_pred) -> dict:
    return {
        "mae": round(float(mean_absolute_error(y_true, y_pred)), 4),
        "rmse": round(float(np.sqrt(mean_squared_error(y_true, y_pred))), 4),
        "r2": round(float(r2_score(y_true, y_pred)), 4),
        "n": int(len(y_true)),
    }


def split_by_district(df: pd.DataFrame) -> tuple[pd.Index, pd.Index]:
    """Hold out ~20 % of districts per crop, so both crops are represented in the test set."""
    train_idx, test_idx = [], []
    for crop in SUPPORTED_CROPS:
        part = df[df["crop"] == crop]
        gss = GroupShuffleSplit(n_splits=1, test_size=TEST_FRACTION, random_state=SEED)
        tr, te = next(gss.split(part, groups=part["group"]))
        train_idx.extend(part.index[tr])
        test_idx.extend(part.index[te])
    return pd.Index(sorted(train_idx)), pd.Index(sorted(test_idx))


def pipeline_for(name: str, estimator) -> Pipeline:
    return Pipeline([("preprocess", build_preprocessor(scale_numeric=name == "linear_regression")),
                     ("model", estimator)])


def fertilizer_response(model: Pipeline, X: pd.DataFrame, crop: str, grid: list[float]) -> dict:
    """Partial dependence: mean predicted yield when every row's N (resp. P2O5, K2O) is set to each value."""
    rows = X[X["crop"] == crop]
    out = {}
    for col in SCENARIO_NUMERIC:
        curve = []
        for value in grid:
            probe = rows.copy()
            probe[col] = value
            curve.append(round(float(model.predict(probe).mean()), 3))
        out[col] = dict(zip([str(int(v)) for v in grid], curve))
    return out


def git_state() -> dict:
    """HEAD commit plus whether the ML code differed from it when this model was trained."""
    def git(*args: str) -> str:
        return subprocess.run(["git", *args], capture_output=True, text=True, cwd=ML_ROOT, check=True).stdout.strip()
    try:
        return {"commit": git("rev-parse", "--short", "HEAD"),
                "ml_code_uncommitted": bool(git("status", "--porcelain", "--", "app", "training"))}
    except (OSError, subprocess.CalledProcessError):
        return {"commit": None, "ml_code_uncommitted": None}


def main() -> int:
    if not PROCESSED_FILE.exists():
        raise SystemExit(f"{PROCESSED_FILE} missing - run: python -m training.prepare_data")
    df = pd.read_csv(PROCESSED_FILE)
    df["group"] = df["source"] + ":" + df["district"].astype(str)
    X = df[FEATURES].copy()
    y = df[TARGET].to_numpy()
    groups = df["group"].to_numpy()

    train_idx, test_idx = split_by_district(df)
    X_train, X_test = X.loc[train_idx], X.loc[test_idx]
    y_train, y_test = y[train_idx], y[test_idx]
    g_train = groups[train_idx]
    assert not set(g_train) & set(groups[test_idx]), "district leaked between train and test"
    cv = GroupKFold(n_splits=CV_FOLDS, shuffle=True, random_state=SEED)
    print(f"train {len(train_idx)} rows / {len(set(g_train))} districts; "
          f"test {len(test_idx)} rows / {len(set(groups[test_idx]))} districts")

    # Reference point: predict the training mean yield of the crop.
    crop_mean = pd.Series(y_train).groupby(X_train["crop"].to_numpy()).mean()
    baseline = {"test": metrics(y_test, X_test["crop"].map(crop_mean).to_numpy())}

    results: dict[str, dict] = {}
    fitted: dict[str, Pipeline] = {}
    rows = []
    for name, (estimator, grid) in CANDIDATES.items():
        started = time.perf_counter()
        pipe = pipeline_for(name, estimator)
        search = GridSearchCV(pipe, {f"model__{k}": v for k, v in grid.items()} or [{}],
                              scoring={"rmse": "neg_root_mean_squared_error", "mae": "neg_mean_absolute_error",
                                       "r2": "r2"},
                              refit="rmse", cv=cv, n_jobs=1)
        search.fit(X_train, y_train, groups=g_train)
        best = search.best_index_
        cvr = search.cv_results_
        cv_metrics = {
            "rmse_mean": round(float(-cvr["mean_test_rmse"][best]), 4),
            "rmse_std": round(float(cvr["std_test_rmse"][best]), 4),
            "mae_mean": round(float(-cvr["mean_test_mae"][best]), 4),
            "r2_mean": round(float(cvr["mean_test_r2"][best]), 4),
            "r2_std": round(float(cvr["std_test_r2"][best]), 4),
        }
        model = search.best_estimator_
        pred = model.predict(X_test)
        test = metrics(y_test, pred)
        by_crop = {c: metrics(y_test[(X_test["crop"] == c).to_numpy()], pred[(X_test["crop"] == c).to_numpy()])
                   for c in SUPPORTED_CROPS}
        params = {k.removeprefix("model__"): v for k, v in search.best_params_.items()}
        results[name] = {"cv": cv_metrics, "test": test, "test_by_crop": by_crop, "best_params": params,
                         "grid": grid, "fit_seconds": round(time.perf_counter() - started, 1)}
        fitted[name] = model
        rows.append({"model": name, **{f"cv_{k}": v for k, v in cv_metrics.items()},
                     **{f"test_{k}": v for k, v in test.items() if k != "n"}, "best_params": json.dumps(params)})
        print(f"{name:18s} CV RMSE {cv_metrics['rmse_mean']:.4f}±{cv_metrics['rmse_std']:.4f} "
              f"R2 {cv_metrics['r2_mean']:.4f} | test MAE {test['mae']:.4f} RMSE {test['rmse']:.4f} "
              f"R2 {test['r2']:.4f} | {params}")

    selected = min(results, key=lambda n: results[n]["cv"]["rmse_mean"])
    model = fitted[selected]
    print(f"selected: {selected} (lowest mean grouped-CV RMSE)")

    # How optimistic would a naive random split have been? (same model/params, rows shuffled across districts)
    random_cv = cross_val_score(model, X_train, y_train, cv=KFold(CV_FOLDS, shuffle=True, random_state=SEED),
                                scoring="neg_root_mean_squared_error")
    random_cv_r2 = cross_val_score(model, X_train, y_train, cv=KFold(CV_FOLDS, shuffle=True, random_state=SEED),
                                   scoring="r2")

    perm = permutation_importance(model, X_test, y_test, n_repeats=10, random_state=SEED,
                                  scoring="neg_root_mean_squared_error")
    importance = sorted(({"feature": f, "rmse_increase": round(float(m), 4), "std": round(float(s), 4)}
                         for f, m, s in zip(FEATURES, perm.importances_mean, perm.importances_std)),
                        key=lambda d: -d["rmse_increase"])
    response = {c: fertilizer_response(model, X_test, c, [0, 40, 80, 120, 160, 200]) for c in SUPPORTED_CROPS}

    # Supported input range per crop (0.5th-99.5th percentile of training data); inference clips to it.
    ranges = {}
    for crop in SUPPORTED_CROPS:
        part = X_train[X_train["crop"] == crop]
        ranges[crop] = {c: [round(float(part[c].quantile(0.005)), 2), round(float(part[c].quantile(0.995)), 2)]
                        for c in NUMERIC_FEATURES}

    trained_at = datetime.now(timezone.utc)
    version = f"yield-lds2018-{selected.replace('_', '')}-{trained_at:%Y%m%d}"
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    model_path = ARTIFACTS_DIR / "yield_model.joblib"
    joblib.dump(model, model_path, compress=3)

    metadata = {
        "model_version": version,
        "trained_at": trained_at.isoformat(timespec="seconds"),
        "git": git_state(),
        "data_label": DATA_LABEL,
        "problem": "regression: grain yield (t/ha) for a candidate fertilizer scenario",
        "target": {"name": TARGET, "unit": "t/ha",
                   "definition": "farmer-reported grain production of the largest plot / plot area "
                                 "(survey field L-tonPerHectare)"},
        "algorithm": selected,
        "hyperparameters": results[selected]["best_params"],
        "features": {"all": FEATURES, "numeric": NUMERIC_FEATURES, "binary": BINARY_FEATURES,
                     "categorical": CATEGORICAL_FEATURES, "scenario": SCENARIO_NUMERIC + SCENARIO_BINARY,
                     "categories": {k: list(v) for k, v in CATEGORIES.items()}},
        "supported_crops": list(SUPPORTED_CROPS),
        "supported_ranges": ranges,
        "leakage_excluded_fields": LEAKAGE_EXCLUDED,
        "datasets": [{"key": d.key, "title": d.title, "handle": d.handle, "url": d.url,
                      "version": d.dataverse_version, "released": d.released, "md5": d.md5,
                      "paper": d.paper, "licence": d.licence} for d in DATASETS],
        "processed_data": {"file": PROCESSED_FILE.name, "md5": md5_of(PROCESSED_FILE), "rows": int(len(df))},
        "split": {"method": "GroupShuffleSplit by district, per crop", "test_fraction_of_districts": TEST_FRACTION,
                  "train_rows": int(len(train_idx)), "test_rows": int(len(test_idx)),
                  "train_districts": int(len(set(g_train))), "test_districts": int(len(set(groups[test_idx]))),
                  "test_districts_list": sorted(set(groups[test_idx]))},
        "cross_validation": {"method": f"GroupKFold({CV_FOLDS}, shuffle=True) by district on the training split",
                             "selection_metric": "mean CV RMSE (lowest wins)"},
        "random_seed": SEED,
        "metrics": {"baseline_crop_mean": baseline, **results},
        "selected_model_random_kfold_cv": {
            "rmse_mean": round(float(-random_cv.mean()), 4), "r2_mean": round(float(random_cv_r2.mean()), 4),
            "note": "rows shuffled across districts; optimistic, shown only to quantify spatial leakage"},
        "permutation_importance_test": importance,
        "fertilizer_partial_dependence_test": response,
        "artifact": {"file": model_path.name, "sha256": hashlib.sha256(model_path.read_bytes()).hexdigest(),
                     "bytes": model_path.stat().st_size},
        "environment": {"python": platform.python_version(), "scikit-learn": sklearn.__version__,
                        "xgboost": xgboost.__version__, "pandas": pd.__version__, "numpy": np.__version__},
    }
    (ARTIFACTS_DIR / "metadata.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    pd.DataFrame(rows).to_csv(REPORTS_DIR / "model_comparison.csv", index=False)
    (REPORTS_DIR / "evaluation_report.md").write_text(render_report(metadata), encoding="utf-8")
    print(f"saved {model_path} ({metadata['artifact']['bytes']} bytes), version {version}")
    return 0


def render_report(m: dict) -> str:
    lines = [
        f"# Yield model evaluation — {m['model_version']}",
        "",
        f"_Generated by `python -m training.train` at {m['trained_at']} (git {m['git']['commit']}, "
        f"uncommitted ML code: {m['git']['ml_code_uncommitted']}). "
        "Every number below is written by the script; none is edited by hand._",
        "",
        f"**Data:** {m['data_label']}",
        "",
        f"Split: {m['split']['method']} — train {m['split']['train_rows']} rows / {m['split']['train_districts']} "
        f"districts, test {m['split']['test_rows']} rows / {m['split']['test_districts']} districts. "
        f"CV: {m['cross_validation']['method']}. Seed {m['random_seed']}.",
        "",
        "## Model comparison (yield, t/ha)",
        "",
        "| Model | CV RMSE (mean ± sd) | CV MAE | CV R² | Test MAE | Test RMSE | Test R² | Best params |",
        "|---|---|---|---|---|---|---|---|",
    ]
    b = m["metrics"]["baseline_crop_mean"]["test"]
    lines.append(f"| baseline: crop mean | — | — | — | {b['mae']} | {b['rmse']} | {b['r2']} | — |")
    for name in CANDIDATES:
        r = m["metrics"][name]
        mark = " **(selected)**" if name == m["algorithm"] else ""
        lines.append(f"| {name}{mark} | {r['cv']['rmse_mean']} ± {r['cv']['rmse_std']} | {r['cv']['mae_mean']} | "
                     f"{r['cv']['r2_mean']} | {r['test']['mae']} | {r['test']['rmse']} | {r['test']['r2']} | "
                     f"`{json.dumps(r['best_params'])}` |")
    sel = m["metrics"][m["algorithm"]]
    lines += ["", "## Selected model by crop (test set)", "", "| Crop | n | MAE | RMSE | R² |", "|---|---|---|---|---|"]
    for crop, r in sel["test_by_crop"].items():
        lines.append(f"| {crop} | {r['n']} | {r['mae']} | {r['rmse']} | {r['r2']} |")
    rc = m["selected_model_random_kfold_cv"]
    lines += ["", "## Spatial-leakage check",
              "", f"Same model with a naive random 5-fold CV (rows from the same district on both sides): "
              f"RMSE {rc['rmse_mean']}, R² {rc['r2_mean']} vs grouped CV RMSE {sel['cv']['rmse_mean']}, "
              f"R² {sel['cv']['r2_mean']}. The grouped numbers are the ones reported.",
              "", "## Permutation importance (test set, RMSE increase when the feature is shuffled)", "",
              "| Feature | RMSE increase | sd |", "|---|---|---|"]
    for d in m["permutation_importance_test"]:
        lines.append(f"| {d['feature']} | {d['rmse_increase']} | {d['std']} |")
    lines += ["", "## Fertilizer response learned by the model (partial dependence, test rows)", "",
              "Mean predicted yield (t/ha) when every test row's nutrient dose is set to the value shown, "
              "other inputs unchanged. Learned from observational data: it is an association, not a causal "
              "dose-response curve.", ""]
    for crop, curves in m["fertilizer_partial_dependence_test"].items():
        grid = list(next(iter(curves.values())).keys())
        lines += [f"**{crop}**", "", "| kg/ha | " + " | ".join(grid) + " |", "|---|" + "---|" * len(grid)]
        for col, curve in curves.items():
            lines.append(f"| {col} | " + " | ".join(str(v) for v in curve.values()) + " |")
        lines.append("")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.exit(main())
