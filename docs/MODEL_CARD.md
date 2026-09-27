# Model card — crop yield model `yield-lds2018-xgboost-20260927`

> Trained on **real public survey data** (CIMMYT CSISA Landscape Diagnostic Survey 2018, farmer-reported
> yield). Observational data, not a controlled fertilizer trial. No synthetic data. See [DATA_CARD.md](DATA_CARD.md).
> All numbers here are copied from `ml-service/artifacts/metadata.json` / `ml-service/reports/evaluation_report.md`,
> which `python -m training.train` writes. None was edited by hand.

## Purpose and scope

Predict grain yield (t/ha) of **wheat or rice** for a **candidate fertilizer scenario** on a given field.
In the architecture the model only **compares plans that the requirement engine and the LP optimizer already
produced**:

```
Agricultural rules → nutrient requirement → SciPy optimizer → feasible plans → ML yield prediction → scoring
```

It never prescribes or resizes a dose. Out of scope: maize, other crops, other countries, and optimal-dose
derivation (the data is observational, see Limitations).

## Inputs (feature contract, `ml-service/app/features.py`)

| Group | Features |
|---|---|
| Scenario (varied per plan) | `n_kg_ha`, `p2o5_kg_ha`, `k2o_kg_ha`, `zn_applied` |
| Field / season context | `sowing_day`, `irrigation_available`, `fym_applied`, `crop`, `state`, `soil_texture`, `variety_type`, `previous_crop` |

Each feature is known before fertilizer is applied (justification per feature in DATA_CARD §5). Inference
input is `app/schemas.py::YieldScenario` (takes a `sowing_date` and converts it with the same function used in
training). Numeric inputs outside the per-crop 0.5–99.5th percentile training range are clipped and the
response is flagged `extrapolation: true`.

**Target:** `yield_t_ha`, farmer-reported grain production of the largest plot ÷ plot area.

## Training methodology

- 15,399 plots after cleaning (wheat 7,622; rice 7,777; 88 districts).
- **Test set:** 20 % of districts per crop held out entirely (`GroupShuffleSplit`, seed 42), giving 12,250 train rows
  from 70 districts and 3,149 test rows from 18 unseen districts. A random row split would put neighbouring farms from the
  same village on both sides; see the leakage check below.
- **Model selection:** 5-fold `GroupKFold` (by district, shuffled, seed 42) on the training split with a grid
  search per model, scored by RMSE. The rule, fixed before looking at test results: **lowest mean CV RMSE wins**.
  The test set is used once, for reporting.
- Preprocessing inside one sklearn `Pipeline`: median imputation (numeric), most-frequent imputation
  (binary/categorical), one-hot with fixed category lists (unknown → all zeros), standard scaling for linear
  regression only.
- The served artifact is the selected pipeline fitted on the training split, so the test metrics describe
  exactly the served model.

## Results (t/ha)

| Model | CV RMSE (mean ± sd) | CV MAE | CV R² | Test MAE | Test RMSE | Test R² |
|---|---|---|---|---|---|---|
| baseline: crop mean | — | — | — | 1.024 | 1.4067 | 0.299 |
| Linear Regression | 1.0303 ± 0.041 | 0.7915 | 0.4649 | 0.8221 | 1.0611 | 0.6011 |
| Decision Tree (depth 8, leaf 50) | 1.0859 ± 0.046 | 0.8209 | 0.403 | 0.8808 | 1.1387 | 0.5407 |
| Random Forest (300 trees, depth 16, leaf 10, max_features 0.33) | 1.0312 ± 0.0296 | 0.7813 | 0.4627 | 0.8344 | 1.0807 | 0.5862 |
| **XGBoost (selected)** (300 trees, depth 5, lr 0.03, min_child_weight 20, subsample/colsample 0.8) | **1.022 ± 0.0276** | 0.7751 | 0.4717 | 0.851 | 1.1045 | 0.5678 |

Per crop, test set:

| Model | Wheat MAE / RMSE / R² (n = 1,613) | Rice MAE / RMSE / R² (n = 1,536) |
|---|---|---|
| Linear Regression | 0.5663 / 0.732 / 0.2577 | 1.0907 / 1.3212 / 0.4262 |
| Decision Tree | 0.5714 / 0.737 / 0.2475 | 1.2057 / 1.4449 / 0.3137 |
| Random Forest | 0.553 / 0.7186 / 0.2845 | 1.1298 / 1.361 / 0.3911 |
| **XGBoost** | 0.5502 / 0.7133 / 0.2951 | 1.1669 / 1.4024 / 0.3536 |

### How to read these numbers

- Pooled R² (≈ 0.47 CV, 0.57 test) is inflated by the rice/wheat difference: predicting each crop's mean alone
  gives test R² 0.30. The **within-crop R² of 0.30 (wheat) and 0.35 (rice)** is the honest measure of how much
  field-to-field variation the model explains.
- The ceiling is low because the target is noisy: farmer-reported vs crop-cut yield correlate only r = 0.56.
- **Spatial leakage check:** the same XGBoost model under a naive random 5-fold CV scores RMSE 0.8654 /
  R² 0.6323, against 1.022 / 0.4717 under district-grouped CV. Grouped numbers are reported.

### Why XGBoost was selected

1. It has the lowest mean grouped-CV RMSE (the pre-declared rule) and the lowest CV fold spread. The gaps are
   small: Linear Regression and Random Forest are within about one CV standard deviation.
2. Linear Regression scores better on the held-out districts (test R² 0.60 vs 0.57, mainly on rice). We did
   **not** switch to it because of that, which would be choosing on the test set. It is recorded here instead.
3. For this use case the linear model has a structural problem: its fertilizer effect is a straight line, so
   "more N is always better by the same amount" and it cannot represent diminishing returns. The tree
   ensembles can.

## What the model learned about fertilizer

Permutation importance on the test set (RMSE increase when shuffled): crop 0.623, state 0.2466, sowing_day
0.0776, **n_kg_ha 0.0511**, p2o5_kg_ha 0.0186, k2o_kg_ha 0.0137, then the other context features at ≤ 0.007.
Fertilizer doses therefore **do** change predictions, but the effect is small next to location and planting
date.

Partial dependence (mean predicted yield on test rows when the dose is set to the value shown):

| kg/ha | 0 | 40 | 80 | 120 | 160 | 200 |
|---|---|---|---|---|---|---|
| Wheat, N | 2.38 | 2.469 | 2.953 | 3.046 | 3.157 | 3.232 |
| Rice, N | 4.382 | 4.223 | 4.37 | 4.485 | 4.789 | 4.857 |

The wheat N response rises with diminishing returns, which is agronomically plausible. The rice curve dips
between 0 and 40 kg N, an artefact of the sparse, confounded low-dose region. Neither curve turns down at high
doses, so **the model cannot penalise over-application**. Penalising excess is the optimizer's and the scoring
step's job (architecture §7), not the model's.

## Artifact and reproducibility

| Item | Value |
|---|---|
| Artifact | `ml-service/artifacts/yield_model.joblib` (192,201 bytes, full sklearn Pipeline incl. preprocessing), sha256 `17284e7a939c46fc97e0f28faf78ebe4b91591005efba64bfb219c474dc41059` |
| Metadata | `ml-service/artifacts/metadata.json`: features, categories, supported ranges, leakage exclusions, datasets + MD5, split, seeds, grids, best params, all metrics, library versions, git state |
| Data | LDS wheat v2.0 (md5 `26b075c4…`), LDS rice v3.0 (md5 `d61e6fe9…`), processed table md5 `b41094e485247f34ffcf9f38905082bf` |
| Seed | 42 (split, CV shuffling, models) |
| Environment | Python 3.13.11, scikit-learn 1.9.1, xgboost 3.4.1, pandas 3.0.6, numpy 2.5.3 (`ml-service/requirements.txt`) |
| Reproduce | `scripts/ml.sh pipeline` (or `.\scripts\ml.ps1 pipeline`). Two full runs gave identical metrics and a byte-identical artifact |

The loader refuses an artifact whose sha256 differs from `metadata.json` or whose feature list differs from
`app/features.py`, and it logs a warning if the scikit-learn/xgboost versions differ from the training ones. Missing,
unreadable or undeserialisable artifacts are also refused (`ModelLoadError` with a client-safe reason).

## Serving (Milestone 6)

`POST /predict-yield` on the ML service ([ML_API.md](ML_API.md)) calls `YieldModel.predict` on the artifact loaded once
at startup. There is no retraining and no change to the model. Inputs outside `supported_ranges` are clipped exactly
as before, and the response now also lists each clipped input with the requested value, the value used and the
bounds. Crops other than wheat and rice get HTTP 422 `unsupported-crop`. `GET /model/info` publishes the metadata above
without file paths, and `feature_version` is a sha256 fingerprint of the artifact's recorded feature contract.

Inference fix in M6: the canonical category values `PULSE` and `OTHER` sent by a client are now kept as they are.
Before, `PULSE` was re-grouped to OTHER, because only raw crop names such as "Lentil" were recognised. The raw survey
contains no literal `PULSE` value (it uses `PULSES`), so the training table is unchanged. It was re-derived with md5
`b41094e4…`, identical to the one used for training.

## Limitations and appropriate use

- Associations from observational data. Use the model to *rank* a few feasible plans; do not read its curve
  as an agronomic dose-response or use it to pick a dose.
- Accuracy is modest (wheat MAE 0.55 t/ha, rice MAE 1.17 t/ha on unseen districts). The UI should show yield as
  an estimate with this error, not as a precise number.
- Soil test values (N/P/K/pH/OC) and weather are **not** model inputs, so the model gives the same answer for
  two fields that differ only in soil test.
- Wheat and rice only; eight Indian states; one season (2018).
- Survey value `REDGRAM` (pigeon pea, a pulse) was grouped as OTHER, not PULSE, during training (2 raw rice rows). Fixing
  it requires retraining, so inference keeps the same grouping to match the model.
- Prototype decision support. Recommendations must be validated with soil testing and local agronomic advice.
