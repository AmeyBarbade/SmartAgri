# Data card — yield model training data (Milestone 3)

> **Status of the data:** **real public survey data**. No synthetic or simulated rows are used anywhere in
> training or evaluation. Features marked *derived* below are deterministic transformations of the real
> survey answers. The few test fixtures in `ml-service/tests/` are hand-built unit-test rows, not data.

## 1. Requirement

Architecture §6 needs a dataset in which **yield can be compared across fertilizer scenarios**: fertilizer
must be recorded as **what was actually applied, and how much**, per field, together with yield and field
context. Crop + yield alone is not sufficient.

## 2. Candidates investigated

### 2.1 Serious candidates (downloaded and inspected)

| # | Item | CIMMYT LDS Wheat 2018 | CIMMYT LDS Rice 2018 | Kaggle "Crop Yield in Indian States" | TAMASA Nigeria nutrient omission trials 2016 |
|---|---|---|---|---|---|
| 1 | Source | CIMMYT Dataverse, [hdl:11529/10548507](https://hdl.handle.net/11529/10548507) (CSISA project, ICAR KVKs) | CIMMYT Dataverse, [hdl:11529/10548656](https://hdl.handle.net/11529/10548656) | [kaggle.com/datasets/akshatgupta7/crop-yield-in-indian-states-dataset](https://www.kaggle.com/datasets/akshatgupta7/crop-yield-in-indian-states-dataset) (inspected via a public GitHub mirror of `crop_yield.csv`; Kaggle needs login) | CIMMYT Dataverse, [hdl:11529/246980](https://hdl.handle.net/11529/246980) |
| 2 | Name | Landscape diagnostic survey data of wheat production practices and yield of 2018 from eastern India (v2.0, released 2024-01-30) | Large-scale data of crop production practices applied by farmers on their largest rice plot during 2018 in eight Indian states (v3.0, released 2022-08-26) | Agricultural Crop Yield in Indian States Dataset | TAMASA Nigeria. Nutrient omission trials (NOT) yield data for 2016 |
| 3 | Licence | Data paper (Ajay et al., ODJAR, doi:10.18174/odjar.v7i0.17959) CC BY 4.0; Dataverse record: "CIMMYT policy", no explicit licence field | Data paper (Ajay et al. 2022, *Data in Brief*, PMC9679526) CC BY 4.0; Dataverse record as for wheat | Not verified (rejected on content) | Dataverse: no licence field |
| 4 | Rows | 7,648 plots | 8,355 plots (194 exact duplicate rows) | 19,689 | — (file not retrievable) |
| 5 | Columns | 315 (64 entirely empty) | 218 (5 entirely empty) | 10 | — |
| 6 | Missing values | 60 % of all cells, almost all structural (skip logic: e.g. top-dress 3 questions empty when there was no third top-dress). In the columns we use: none, except complex-fertilizer grade (empty when no complex fertilizer used) | 53 % of cells, structural; used columns: none except grade (as wheat) | 0 | — |
| 7 | Target | `L-tonPerHectare`: farmer-reported grain yield of the largest plot, t/ha | same | `Yield` = Production / Area (state-level) | Grain yield per plot |
| 8 | Fertilizer variables | kg **per plot** of DAP, Urea, MoP, SSP, TSP, NPK (+grade), NPKS (+grade), ZnSO4, gypsum, boron, "other"; separately for basal and up to 3 top-dressings, with days after sowing | same structure | `Fertilizer` (kg, state total) | Treatment code (Control/PK/NK/NP/NPK/NPK+micros) |
| 9 | Soil variables | Farmer-perceived texture (light/medium/heavy), drainage class, perceived quality, soil-health-card status. **No lab N/P/K/pH/OC** | same | none | Site soil samples (per paper) |
| 10 | Weather variables | None measured. Farmer-reported drought severity/stage (post-season), irrigation count | Same + flood severity | `Annual_Rainfall` (state) | — |
| 11 | Fertilizer application actually recorded? | **Yes**, per farmer, per product, per split | **Yes** | **No** (see §2.3) | Yes (designed treatments) |
| 12 | Quantity recorded? | **Yes** (kg per plot + plot area → kg/ha) | **Yes** | No meaningful quantity | Rates fixed by design |
| 13 | Can it distinguish fertilizer plans? | **Yes** — continuous N, P₂O₅, K₂O doses vary widely between farmers (wheat N: 1st–99th pct ≈ 41–204 kg/ha) | **Yes** (rice N ≈ 0–272 kg/ha) | **No** | Only on/off omission contrasts |
| 14 | Potential leakage | Harvest date, crop duration, production, crop-cut yield, lodging, drought/flood/pest severity, irrigation count, sale price, "yield vs last 5 years" — all excluded (§5) | same | `Production` (Yield = Production/Area) | — |
| 15 | Major limitations | Observational (farmers choose doses → confounding); farmer-reported yield; one season; Bihar + eastern UP only; no soil tests or weather | Same; 8 states; no GPS (removed for anonymity) | Fertilizer column is synthetic in effect | File missing on server (HTTP 404, "Failed to locate physical file"); maize/Nigeria only |
| — | **Decision** | **Selected** | **Selected** | **Rejected** | **Rejected (unavailable)** |

### 2.2 Screened out without download (no usable target or no applied-fertilizer quantity)

| Dataset | Why rejected (from its published schema) |
|---|---|
| Kaggle "Crop Recommendation" (atharvaingle) — N, P, K, temperature, humidity, pH, rainfall, label | No yield, no fertilizer applied; classification data, soil values are not linked to outcomes |
| Kaggle "Fertilizer Prediction" — temperature, humidity, moisture, soil type, crop type, N, K, P, fertilizer name | No yield, no quantity; the label is a fertilizer *name* |
| Kaggle "Agriculture Crop Yield" (1 M rows) — region, soil type, crop, rainfall, temperature, `Fertilizer_Used`, `Irrigation_Used`, … | Fertilizer is a yes/no flag (no quantity, no type) |
| FAO-based "yield_df" (country × year) | No fertilizer at all; country-level |
| ICRISAT District Level Database | Real, but fertilizer is total district consumption across *all* crops — cannot be attributed to a crop or field |

### 2.3 Evidence for rejecting the Kaggle "Indian States" fertilizer column

Measured on the 19,689-row file: `Fertilizer / Area` is **identical for every crop and every state within a
year** (within-year coefficient of variation ≤ 4.5 × 10⁻¹¹; e.g. 1997 = 95.17 kg/ha for all rows), and
`corr(Fertilizer, Area) = 0.973`. The column is therefore *Area × one national per-hectare constant*: it
carries no information about how much fertilizer a crop or region actually received, and a model would
only learn "bigger area → more fertilizer". `Pesticide` behaves the same way. Yield also equals
Production / Area (median relative difference 5 %), so `Production` would be direct target leakage.

## 3. Decision

**Use the two CIMMYT CSISA Landscape Diagnostic Surveys (wheat 2018, rice 2018).** They are the only candidates
found that record, per real field, *which* fertilizer products were applied, *how much*, and the resulting
yield. That is exactly the variation the model needs to compare fertilizer plans. The two files come from
the same survey programme, questionnaire and codebook (same column names), so combining them is not merging
unrelated datasets; `crop` is a feature.

**What the data cannot provide** (and we do not fake): lab soil N/P/K/pH, measured weather, maize. Those
inputs remain the job of the deterministic nutrient-requirement engine (Milestone 4) and weather rules
(Milestone 10). The yield model does not claim to respond to them.

## 4. Pipeline and cleaning (`ml-service/training/`)

`download_data.py` fetches both files and verifies MD5 (wheat `26b075c43ab9f84fc9520e82e11e612f`, rice
`d61e6fe94d20e5a8362d1d9943c8fdb8`). `prepare_data.py` runs `validate_raw → tidy → clean` from `lds.py` and
writes `data/processed/lds_wheat_rice_2018.csv` (git-ignored, regenerated) and
`ml-service/reports/data_validation.json`.

**Validation** (fails loudly): required columns present, one or more application columns per product, fertilizer
amounts and areas numeric and non-negative, unique record keys where a key exists.

**Cleaning rules, in order** (actual counts from the run):

| Rule | Why | Removed |
|---|---|---|
| Exact duplicate survey rows | Rice file has no record key and 194 identical rows | 194 (rice) |
| Main season only: wheat *rabi*; rice *kharif/aman* | Boro/rabi rice is a different irrigated system with too few rows (297) to model; 5 wheat rows labelled *kharif* | 302 |
| Yield in 0.3–10 t/ha | Outside is not credible for farmer fields here | 2 |
| Plot ≥ 0.02 ha | Per-ha conversion of tiny plots multiplies rounding errors > 50× | 26 |
| Sowing date parsed and inside season window (wheat 1 Oct–31 Jan, rice 1 May–30 Sep) | Out-of-window dates are entry errors or other seasons | 48 |
| N ≤ 400, P₂O₅ ≤ 250, K₂O ≤ 250 kg/ha | > 2.5× typical recommendations — entry/unit errors | 13 + 14 + 5 |

16,003 → **15,399 rows** (wheat 7,622, rice 7,777) from 88 districts.

## 5. Features

Real survey answers → **derived** features (formulas in `ml-service/app/features.py`, shared by training and
inference):

| Feature | Derivation | Why it is available at recommendation time |
|---|---|---|
| `n_kg_ha`, `p2o5_kg_ha`, `k2o_kg_ha` | Σ over basal + top-dress applications of kg product × grade % ÷ plot ha. Grades: Urea 46-0-0, DAP 18-46-0, MoP 0-0-60, SSP 0-16-0, TSP 0-46-0; NPK/NPKS from the reported grade | **They are the scenario** — each candidate plan specifies them |
| `zn_applied` | ZnSO4 used (yes/no) | Part of the plan |
| `sowing_day` | Days from 1 Oct (wheat) / 1 May (rice) to sowing/transplanting | Field's sowing date (`fields.sowing_date`) |
| `irrigation_available` | Survey "irrigation available" | Field irrigation type |
| `fym_applied` | Farmyard manure/compost applied (yes/no) | Farmer's plan / previous-usage input |
| `crop` | Source file | Field crop |
| `state` | Normalised state name | Farm location |
| `soil_texture` | Farmer-reported light/medium/heavy | Field soil type |
| `variety_type` | Improved / hybrid / local ("Basmati" → improved; "unknown" → missing, imputed) | Chosen at sowing |
| `previous_crop` | Grouped: rice, wheat, fallow, pulse (grain legumes), maize, other | `fields.previous_crop` |

**Excluded as leakage** (full list with reasons in `app/features.py::LEAKAGE_EXCLUDED` and `metadata.json`):
yield in any unit, production, crop-cut yield, harvest date, crop duration, harvest/threshing method, sale
prices, "yield vs last five years", drought/flood/weed/insect/disease severity, lodging %, number of
irrigations (reacts to the season's rainfall), herbicide/weeding counts (react to observed weeds),
fertilizer-on-time/delay, interview date, enumerator device ID. Spearman correlation with yield of three
excluded numeric fields shows what a leaky model could have exploited: crop duration 0.35 (wheat), irrigation
count 0.34 (wheat) / 0.44 (rice). `prepare_data.py` aborts if any excluded field is in the feature list, and
`tests/test_features.py` asserts it.

**Assumptions**
- Plot area `C-q306_cropLarestAreaAcre` (survey-computed acres) × 0.40469 = ha.
- Per-application amounts are used, not the wheat file's survey-computed totals, which disagree with the
  application sums in 193 (DAP) / 179 (urea) / ≤ 9 (others) of 7,648 rows.
- 175 rows used an NPK/NPKS product without a parseable grade → most common grade for that crop
  (wheat NPK 12-32-16, rice NPK 10-26-26, NPKS 20-20-0). Flagged in `complex_grade_imputed`.
- Free-text "other" fertilizers (137 wheat and 602 rice rows, mostly brand names) cannot be mapped to a grade
  and are ignored, so those rows' nutrient totals may be understated. Gypsum and boron carry no N/P/K.
- FYM quantity is recorded in inconsistent units (wet/dry, kg per plot), so only its presence is used.

## 6. Limitations

1. **Observational, not experimental.** Farmers chose their own doses; better-resourced farmers may both apply
   more and manage better. The model learns *associations* between dose and yield, not a causal response
   curve. It is suitable for *ranking a few agronomically valid plans*, not for deriving an optimal dose.
2. **Farmer-reported yield.** On the 302 wheat plots that also had an objective crop cut, farmer-reported and
   crop-cut yields correlate only r = 0.56 (means 3.21 vs 3.47 t/ha). That noise caps achievable accuracy.
   Rice has no crop cuts.
3. **One season (2017-18 rabi / 2018 kharif), Indo-Gangetic plain + 6 other states.** No year-to-year weather
   variation, so the model cannot learn weather effects.
4. **No lab soil tests, no pH, no measured weather.** Soil N/P/K/pH enter the system through the
   requirement engine, not the yield model.
5. **Wheat and rice only.** Maize (seeded in the backend) has no training data; the service rejects it.
6. Few farmers apply very low or very high doses (e.g. < 1 % of wheat plots below 27 kg N/ha), so predictions
   there are extrapolations. Inference clips inputs to the 0.5–99.5th percentile range and flags it.

## 7. Attribution

Ajay, A. et al. *Landscape diagnostic survey data of wheat production practices and yield of 2018 from
eastern India.* CIMMYT Research Data, hdl:11529/10548507; data paper in Open Data Journal for Agricultural Research, doi:10.18174/odjar.v7i0.17959.
Ajay, A. et al. (2022). *Large survey dataset of rice production practices applied by farmers on their largest
farm plot during 2018 in India.* Data in Brief (2022), PMC9679526; CIMMYT Research Data, hdl:11529/10548656.
Raw and processed files are not redistributed in this repository; they are downloaded by
`scripts/ml.sh download`.
