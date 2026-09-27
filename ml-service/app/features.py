"""Feature contract shared by training and inference.

Every transformation that turns a scenario into model input lives here, so the training script and the
serving code cannot drift apart. The fitted sklearn ``Pipeline`` (imputation, encoding, model) is saved
as one artifact; this module only defines the raw feature columns and the deterministic derivations
(fertilizer products -> nutrient kg/ha, sowing date -> day offset, category normalisation).
"""

from __future__ import annotations

import re
from datetime import date

import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

ACRE_TO_HA = 0.40468564224

# Nutrient content (% N, % P2O5, % K2O) of the straight/complex fertilizers recorded in the survey.
# Grades follow the Fertiliser (Control) Order 1985, the same values seeded in the backend (V2 migration).
FERTILIZER_GRADES: dict[str, tuple[float, float, float]] = {
    "UREA": (46.0, 0.0, 0.0),
    "DAP": (18.0, 46.0, 0.0),
    "MOP": (0.0, 0.0, 60.0),
    "SSP": (0.0, 16.0, 0.0),
    "TSP": (0.0, 46.0, 0.0),
}

SUPPORTED_CROPS = ("WHEAT", "RICE")

# Sowing / transplanting date is expressed as days after a fixed crop-specific reference date, so the
# feature means "early vs late planting" independent of the calendar year.
SOWING_REFERENCE = {"WHEAT": (10, 1), "RICE": (5, 1)}  # (month, day)

CATEGORIES: dict[str, tuple[str, ...]] = {
    "crop": SUPPORTED_CROPS,
    "state": (
        "ANDHRA_PRADESH", "BIHAR", "CHHATTISGARH", "HARYANA", "ODISHA", "PUNJAB", "UTTAR_PRADESH",
        "WEST_BENGAL",
    ),
    "soil_texture": ("LIGHT", "MEDIUM", "HEAVY"),
    "variety_type": ("IMPROVED", "HYBRID", "LOCAL"),
    "previous_crop": ("RICE", "WHEAT", "FALLOW", "PULSE", "MAIZE", "OTHER"),
}

# Features describing the field and the season plan (known before fertilizer is applied).
CONTEXT_NUMERIC = ["sowing_day"]
CONTEXT_BINARY = ["irrigation_available", "fym_applied"]
CONTEXT_CATEGORICAL = list(CATEGORIES)

# Features describing the candidate fertilizer scenario (what the optimizer varies).
SCENARIO_NUMERIC = ["n_kg_ha", "p2o5_kg_ha", "k2o_kg_ha"]
SCENARIO_BINARY = ["zn_applied"]

NUMERIC_FEATURES = SCENARIO_NUMERIC + CONTEXT_NUMERIC
BINARY_FEATURES = SCENARIO_BINARY + CONTEXT_BINARY
CATEGORICAL_FEATURES = CONTEXT_CATEGORICAL
FEATURES = NUMERIC_FEATURES + BINARY_FEATURES + CATEGORICAL_FEATURES

TARGET = "yield_t_ha"

# Raw survey fields that must never become features: they are only known after sowing decisions are
# made, after the season's weather has happened, or after harvest (or they encode the target).
LEAKAGE_EXCLUDED = {
    "L-tonPerHectare": "target itself",
    "L-quintalPerAcre": "target in other units",
    "L-q605_totalGrainYieldQUINTAL": "harvest outcome (production)",
    "L-q606_largestPlotYieldQUNITAL": "harvest outcome (production of the plot)",
    "B-grainYield_tonPerHa": "crop-cut yield (outcome)",
    "L-q601_harvestDate": "known only at harvest",
    "L-cropDurationDays": "sowing-to-harvest duration, known only at harvest",
    "L-q602_harvestMethod": "harvest-time practice",
    "L-q604_threshing": "post-harvest practice",
    "L-q607_farmGatePrice": "post-harvest market outcome",
    "L-q608_fiveYearGProd": "farmer's post-harvest judgement of this season's yield",
    "I-q5501_droughtGS": "in-season weather outcome",
    "I-q5502_droughtSeverity": "in-season weather outcome",
    "I-q5503_floodGS": "in-season weather outcome",
    "I-q5504_floodSeverity": "in-season weather outcome",
    "I-q5505_weedSeverity": "in-season outcome",
    "I-q5506_insectSeverity": "in-season outcome",
    "I-q5509_diseaseSeverity": "in-season outcome",
    "I-q5512_lodgingPercent": "in-season outcome, strongly tied to final yield",
    "G-q5305_irrigTimes": "number of irrigations reacts to the season's rainfall (weather outcome)",
    "J-herbAppTimes": "reactive in-season management (depends on observed weed pressure)",
    "J-manualWeedTimes": "reactive in-season management (depends on observed weed pressure)",
    "F-q5287_fertOnTime": "whether fertilizer arrived on time, only known during the season",
    "F-q5288_avgDelayWeeks": "fertilizer delay, only known during the season",
    "M-q706_cropSP": "sale price, post-harvest",
    "M-q707_cropAvgSP": "sale price, post-harvest",
    "collectionDate": "interview date, after harvest",
    "deviceid": "enumerator device, not an agronomic variable (can encode interviewer bias)",
}


def normalise_token(value: object) -> str | None:
    """Upper-case, strip and replace separators so 'Uttar Pradesh', 'UttarPradesh' and 'uttar_pradesh' match."""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return None
    text = re.sub(r"(?<=[a-z])(?=[A-Z])", "_", str(value).strip())
    text = re.sub(r"[\s\-]+", "_", text).upper()
    return text or None


_STATE_ALIASES = {"CHATTISGARH": "CHHATTISGARH", "WESTBENGAL": "WEST_BENGAL", "UTTARPRADESH": "UTTAR_PRADESH"}
_VARIETY_ALIASES = {"BASMATI": "IMPROVED"}
_PULSES = {"LENTIL", "MUNGBEAN", "GREENGRAM", "BLACKGRAM", "GRAM", "CHICKPEA", "PULSES", "PIGEONPEA",
           "SOYBEAN", "PEA", "LATHYRUS", "KHESARI"}


def normalise_state(value: object) -> str | None:
    token = normalise_token(value)
    return _STATE_ALIASES.get(token, token)


def normalise_variety(value: object) -> str | None:
    token = normalise_token(value)
    token = _VARIETY_ALIASES.get(token, token)
    return token if token in CATEGORIES["variety_type"] else None


def normalise_previous_crop(value: object) -> str | None:
    token = normalise_token(value)
    if token is None:
        return None
    if token in CATEGORIES["previous_crop"]:  # canonical values (incl. PULSE, OTHER) map to themselves
        return token
    if token in _PULSES:
        return "PULSE"
    return "OTHER"


def sowing_day(crop: str, sown_on: date) -> int:
    """Days between the crop's reference date and the sowing/transplanting date.

    Wheat (rabi) reference is 1 October of the season's start year; rice (kharif) reference is 1 May.
    """
    month, day = SOWING_REFERENCE[crop]
    year = sown_on.year
    if crop == "WHEAT" and sown_on.month < 7:  # Jan-Jun sowing belongs to the season that began last October
        year -= 1
    return (sown_on - date(year, month, day)).days


def nutrients_from_products(products_kg_ha: dict[str, float],
                            complex_grades: dict[str, tuple[float, float, float]] | None = None
                            ) -> tuple[float, float, float]:
    """Convert product doses (kg product/ha) into nutrient doses (kg N, P2O5, K2O per ha)."""
    grades = {**FERTILIZER_GRADES, **(complex_grades or {})}
    n = p = k = 0.0
    for product, kg in products_kg_ha.items():
        if not kg:
            continue
        gn, gp, gk = grades[product.upper()]
        n += kg * gn / 100.0
        p += kg * gp / 100.0
        k += kg * gk / 100.0
    return n, p, k


def build_preprocessor(scale_numeric: bool) -> ColumnTransformer:
    """Imputation + encoding. Scaling only matters for the linear model; trees are scale-invariant."""
    numeric_steps: list = [("impute", SimpleImputer(strategy="median"))]
    if scale_numeric:
        numeric_steps.append(("scale", StandardScaler()))
    categorical = Pipeline([
        ("impute", SimpleImputer(strategy="most_frequent")),
        ("onehot", OneHotEncoder(categories=[list(CATEGORIES[c]) for c in CATEGORICAL_FEATURES],
                                 handle_unknown="ignore", sparse_output=False)),
    ])
    return ColumnTransformer([
        ("num", Pipeline(numeric_steps), NUMERIC_FEATURES),
        ("bin", SimpleImputer(strategy="most_frequent"), BINARY_FEATURES),
        ("cat", categorical, CATEGORICAL_FEATURES),
    ], verbose_feature_names_out=True)


def to_frame(records: list[dict]) -> pd.DataFrame:
    """Order and type raw feature records exactly as the pipeline expects."""
    frame = pd.DataFrame.from_records(records)
    missing = [c for c in FEATURES if c not in frame.columns]
    if missing:
        raise ValueError(f"missing feature columns: {missing}")
    frame = frame[FEATURES].copy()
    for col in NUMERIC_FEATURES + BINARY_FEATURES:
        frame[col] = pd.to_numeric(frame[col], errors="coerce").astype(float)
    for col in CATEGORICAL_FEATURES:
        frame[col] = frame[col].astype(object)
    return frame
