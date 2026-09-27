"""Validation, cleaning and feature derivation for the CSISA Landscape Diagnostic Survey (LDS) files.

raw survey row -> validate_raw (schema/profile) -> tidy (derive features) -> clean (documented filters)
"""

from __future__ import annotations

import re
from datetime import datetime

import numpy as np
import pandas as pd

from app.features import (
    ACRE_TO_HA, FEATURES, FERTILIZER_GRADES, TARGET, normalise_previous_crop, normalise_state,
    normalise_token, normalise_variety, sowing_day,
)

PRODUCTS = ("DAP", "NPK", "Urea", "NPKS", "MoP", "SSP", "TSP", "ZnSO4")
_APPLICATION_COL = re.compile(r"^F-q\d+_(basal|[123]td)(DAP|NPK|Urea|NPKS|MoP|SSP|TSP|ZnSO4)$")

# Raw columns the pipeline reads. Anything else in the survey is ignored.
REQUIRED_COLUMNS = [
    "A-q102_state", "A-q103_district", "A-q117_season", "C-q306_cropLarestAreaAcre",
    "D-q401_soilTexture", "D-q409_varType", "D-prevCrop", "D-q415_seedingSowingTransDate", "E-q5101_FYM",
    "G-q5301_irrigAvail", "F-q51071_gradeNPK", "F-q51211_gradeNPKS", "L-tonPerHectare",
]

# Default grade when a farmer used a complex fertilizer but the grade is missing or unparseable:
# the most common grade reported for that crop in the same survey.
DEFAULT_COMPLEX_GRADE = {
    ("WHEAT", "NPK"): (12.0, 32.0, 16.0),
    ("RICE", "NPK"): (10.0, 26.0, 26.0),
    ("WHEAT", "NPKS"): (20.0, 20.0, 0.0),
    ("RICE", "NPKS"): (20.0, 20.0, 0.0),
}

# Cleaning thresholds (each rule is logged with the number of rows it removes).
SEASONS = {"WHEAT": {"RABI"}, "RICE": {"KHARIF", "AMAN"}}  # main season only; boro/rabi rice is a different system
SOWING_WINDOW = {"WHEAT": (0, 122), "RICE": (0, 152)}      # wheat 1 Oct-31 Jan, rice 1 May-30 Sep
MIN_PLOT_HA = 0.02        # below ~200 m2 the per-hectare conversion multiplies rounding errors by >50x
YIELD_RANGE = (0.3, 10.0)  # t/ha; outside this is not credible for farmer fields in these regions
MAX_NUTRIENT = {"n_kg_ha": 400.0, "p2o5_kg_ha": 250.0, "k2o_kg_ha": 250.0}  # >2.5x typical recommendations


def application_columns(columns) -> dict[str, list[str]]:
    by_product: dict[str, list[str]] = {p: [] for p in PRODUCTS}
    for col in columns:
        m = _APPLICATION_COL.match(col)
        if m:
            by_product[m.group(2)].append(col)
    return by_product


def parse_grade(value: object) -> tuple[float, float, float] | None:
    """'12_32_16', 'Other20-20-0-13', '17_17_17 12_32_16' -> first grade's (N, P2O5, K2O); None if unusable."""
    if not isinstance(value, str) or not value.strip():
        return None
    numbers = re.findall(r"\d+(?:\.\d+)?", value.strip().split(" ")[0])
    if len(numbers) < 3:
        return None
    grade = tuple(float(x) for x in numbers[:3])
    return grade if 0 < sum(grade) <= 100 else None


def validate_raw(raw: pd.DataFrame, crop: str) -> dict:
    """Schema and content checks on a raw file. Raises on anything that would silently corrupt features."""
    missing = [c for c in REQUIRED_COLUMNS if c not in raw.columns]
    if missing:
        raise ValueError(f"{crop}: required columns missing: {missing}")
    apps = application_columns(raw.columns)
    empty_products = [p for p, cols in apps.items() if not cols]
    if empty_products:
        raise ValueError(f"{crop}: no application columns found for {empty_products}")
    if "KEY" in raw.columns and raw["KEY"].duplicated().any():
        raise ValueError(f"{crop}: duplicate survey KEYs")
    numeric = ["C-q306_cropLarestAreaAcre", "L-tonPerHectare"] + [c for cols in apps.values() for c in cols]
    for col in numeric:
        if not pd.api.types.is_numeric_dtype(raw[col]):
            raise ValueError(f"{crop}: column {col} is not numeric")
        if (raw[col] < 0).any():
            raise ValueError(f"{crop}: negative values in {col}")

    report = {
        "rows": int(len(raw)),
        "columns": int(raw.shape[1]),
        "fully_empty_columns": int(raw.isna().all().sum()),
        "exact_duplicate_rows": int(raw.duplicated().sum()),
        "has_record_key": "KEY" in raw.columns,
        "missing_in_used_columns": {c: int(raw[c].isna().sum()) for c in REQUIRED_COLUMNS},
        "application_columns": {p: len(c) for p, c in apps.items()},
        "users_per_product": {p: int((raw[c].fillna(0).sum(axis=1) > 0).sum()) for p, c in apps.items()},
    }
    # The wheat file also carries survey-computed totals; report how often they disagree with the
    # per-application amounts (we use the per-application amounts, which are the primary answers).
    totals = {p: f"F-totAmt{p}" for p in PRODUCTS if f"F-totAmt{p}" in raw.columns}
    if totals:
        report["total_vs_application_mismatch"] = {
            p: int((np.abs(raw[t].fillna(0) - raw[apps[p]].fillna(0).sum(axis=1)) > 0.01).sum())
            for p, t in totals.items()
        }
    if "B-grainYield_tonPerHa" in raw.columns:
        cc = raw["B-grainYield_tonPerHa"]
        mask = cc.notna() & (cc > 0)
        if mask.sum() > 10:
            report["crop_cut_check"] = {
                "plots_with_crop_cut": int(mask.sum()),
                "pearson_r_farmer_vs_crop_cut": round(float(np.corrcoef(cc[mask], raw.loc[mask, "L-tonPerHectare"])[0, 1]), 3),
                "mean_crop_cut_t_ha": round(float(cc[mask].mean()), 3),
                "mean_farmer_reported_t_ha": round(float(raw.loc[mask, "L-tonPerHectare"].mean()), 3),
            }
    return report


def _yes(value: object) -> float:
    token = normalise_token(value)
    if token is None:
        return np.nan
    return 1.0 if token.startswith("YES") else 0.0


def _parse_date(value: object):
    try:
        return datetime.strptime(str(value).strip(), "%d-%m-%Y").date()
    except ValueError:
        return None


def tidy(raw: pd.DataFrame, crop: str, source: str) -> pd.DataFrame:
    """Derive the model feature columns (plus bookkeeping columns) from a validated raw file."""
    apps = application_columns(raw.columns)
    plot_ha = raw["C-q306_cropLarestAreaAcre"].astype(float) * ACRE_TO_HA
    product_kg = {p: raw[cols].fillna(0).sum(axis=1) for p, cols in apps.items()}

    n = pd.Series(0.0, index=raw.index)
    p2o5 = n.copy()
    k2o = n.copy()
    for product, (gn, gp, gk) in FERTILIZER_GRADES.items():
        kg = product_kg[{"UREA": "Urea", "MOP": "MoP"}.get(product, product)]
        n += kg * gn / 100
        p2o5 += kg * gp / 100
        k2o += kg * gk / 100

    grade_imputed = pd.Series(False, index=raw.index)
    for product, grade_col in (("NPK", "F-q51071_gradeNPK"), ("NPKS", "F-q51211_gradeNPKS")):
        parsed = raw[grade_col].map(parse_grade)
        used = product_kg[product] > 0
        imputed = used & parsed.isna()
        grade_imputed |= imputed
        default = DEFAULT_COMPLEX_GRADE[(crop, product)]
        grades = parsed.map(lambda g: g if isinstance(g, tuple) else default)
        kg = product_kg[product]
        n += kg * grades.map(lambda g: g[0]) / 100
        p2o5 += kg * grades.map(lambda g: g[1]) / 100
        k2o += kg * grades.map(lambda g: g[2]) / 100

    sown = raw["D-q415_seedingSowingTransDate"].map(_parse_date)
    out = pd.DataFrame({
        "source": source,
        # The rice file ships without a record key; fall back to the row position in the raw file.
        "record_id": raw["KEY"] if "KEY" in raw.columns else [f"{source}-row{i}" for i in raw.index],
        "raw_duplicate": raw.duplicated().to_numpy(),
        "district": raw["A-q103_district"].map(normalise_token),
        "season": raw["A-q117_season"].map(normalise_token),
        "plot_ha": plot_ha,
        "complex_grade_imputed": grade_imputed,
        # scenario features
        "n_kg_ha": n / plot_ha,
        "p2o5_kg_ha": p2o5 / plot_ha,
        "k2o_kg_ha": k2o / plot_ha,
        "zn_applied": (product_kg["ZnSO4"] > 0).astype(float),
        # context features
        "sowing_day": sown.map(lambda d: sowing_day(crop, d) if d else np.nan),
        "irrigation_available": raw["G-q5301_irrigAvail"].map(_yes),
        "fym_applied": raw["E-q5101_FYM"].map(_yes),
        "crop": crop,
        "state": raw["A-q102_state"].map(normalise_state),
        "soil_texture": raw["D-q401_soilTexture"].map(normalise_token),
        "variety_type": raw["D-q409_varType"].map(normalise_variety),
        "previous_crop": raw["D-prevCrop"].map(normalise_previous_crop),
        TARGET: raw["L-tonPerHectare"].astype(float),
    })
    assert set(FEATURES) <= set(out.columns)
    return out


def clean(frame: pd.DataFrame) -> tuple[pd.DataFrame, list[dict]]:
    """Apply the documented filters in order; return the kept rows and a per-rule log."""
    log: list[dict] = []

    def apply(rule: str, keep: pd.Series) -> None:
        nonlocal frame
        keep = keep.fillna(False).astype(bool)
        removed = frame.loc[~keep]
        log.append({"rule": rule, "removed": int(len(removed)),
                    "removed_by_crop": {k: int(v) for k, v in removed["crop"].value_counts().items()}})
        frame = frame.loc[keep]

    apply("exact duplicate survey rows", ~frame["raw_duplicate"])
    apply("main season only (wheat rabi; rice kharif/aman)",
          frame.apply(lambda r: r["season"] in SEASONS[r["crop"]], axis=1))
    apply("target present", frame[TARGET].notna())
    apply(f"yield within {YIELD_RANGE} t/ha", frame[TARGET].between(*YIELD_RANGE))
    apply(f"plot area >= {MIN_PLOT_HA} ha", frame["plot_ha"] >= MIN_PLOT_HA)
    apply("sowing/transplanting date parsed and inside the crop's season window",
          frame.apply(lambda r: SOWING_WINDOW[r["crop"]][0] <= r["sowing_day"] <= SOWING_WINDOW[r["crop"]][1]
                      if pd.notna(r["sowing_day"]) else False, axis=1))
    for col, cap in MAX_NUTRIENT.items():
        apply(f"{col} <= {cap}", frame[col] <= cap)
    return frame.reset_index(drop=True), log
