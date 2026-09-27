"""Validation / cleaning / feature derivation on small hand-built survey rows (unit fixtures, not training data)."""

import pandas as pd
import pytest

from app.features import ACRE_TO_HA, FEATURES
from training.lds import PRODUCTS, clean, parse_grade, tidy, validate_raw


def raw_row(**overrides) -> dict:
    row = {
        "A-q102_state": "Bihar", "A-q103_district": "Patna", "A-q117_season": "Rabi",
        "C-q306_cropLarestAreaAcre": 1.0, "D-q401_soilTexture": "Medium", "D-q409_varType": "Improved",
        "D-prevCrop": "Rice", "D-q415_seedingSowingTransDate": "15-11-2017", "E-q5101_FYM": "no",
        "G-q5301_irrigAvail": "yes", "F-q51071_gradeNPK": None, "F-q51211_gradeNPKS": None,
        "L-tonPerHectare": 3.0,
    }
    for i, product in enumerate(PRODUCTS):
        row[f"F-q52{i:02d}_basal{product}"] = 0.0
        row[f"F-q53{i:02d}_1td{product}"] = 0.0
    row.update(overrides)
    return row


def frame(*rows) -> pd.DataFrame:
    return pd.DataFrame(list(rows))


def test_per_plot_kg_becomes_nutrient_kg_per_ha():
    # 1 acre plot: 20 kg DAP basal + 40 kg urea top-dress + 10 kg MoP
    raw = frame(raw_row(**{"F-q5200_basalDAP": 20.0, "F-q5302_1tdUrea": 40.0, "F-q5204_basalMoP": 10.0}))
    out = tidy(raw, "WHEAT", "test").iloc[0]
    assert out["n_kg_ha"] == pytest.approx((20 * .18 + 40 * .46) / ACRE_TO_HA)
    assert out["p2o5_kg_ha"] == pytest.approx(20 * .46 / ACRE_TO_HA)
    assert out["k2o_kg_ha"] == pytest.approx(10 * .60 / ACRE_TO_HA)
    assert out["sowing_day"] == 45
    assert out["state"] == "BIHAR" and out["previous_crop"] == "RICE" and out["irrigation_available"] == 1.0


def test_complex_grade_is_parsed_or_imputed():
    parsed = tidy(frame(raw_row(**{"F-q5201_basalNPK": 100.0, "F-q51071_gradeNPK": "Other20-20-13"})), "WHEAT", "t")
    assert parsed.iloc[0]["n_kg_ha"] == pytest.approx(20 / ACRE_TO_HA)
    assert not parsed.iloc[0]["complex_grade_imputed"]
    imputed = tidy(frame(raw_row(**{"F-q5201_basalNPK": 100.0})), "WHEAT", "t")
    assert imputed.iloc[0]["complex_grade_imputed"]
    assert imputed.iloc[0]["p2o5_kg_ha"] == pytest.approx(32 / ACRE_TO_HA)  # wheat default 12-32-16


@pytest.mark.parametrize("text, expected", [
    ("12_32_16", (12, 32, 16)), ("Other20-20-0-13", (20, 20, 0)), ("17_17_17 12_32_16", (17, 17, 17)),
    ("OtherGrommer", None), (None, None), ("Other00", None),
])
def test_parse_grade(text, expected):
    assert parse_grade(text) == expected


def test_clean_applies_documented_filters():
    raw = frame(
        raw_row(),                                                          # kept
        raw_row(**{"A-q117_season": "Kharif"}),                             # wrong season for wheat
        raw_row(**{"C-q306_cropLarestAreaAcre": 0.03}),                    # plot < 0.02 ha
        raw_row(**{"L-tonPerHectare": 12.0}),                               # implausible yield
        raw_row(**{"D-q415_seedingSowingTransDate": "15-03-2018"}),         # outside sowing window
        raw_row(**{"F-q5302_1tdUrea": 500.0}),                              # ~568 kg N/ha > cap
    )
    kept, log = clean(tidy(raw, "WHEAT", "t"))
    assert len(kept) == 1
    assert sum(r["removed"] for r in log) == 5


def test_exact_duplicates_are_removed_once():
    kept, log = clean(tidy(frame(raw_row(), raw_row()), "WHEAT", "t"))
    assert len(kept) == 1
    assert log[0] == {"rule": "exact duplicate survey rows", "removed": 1, "removed_by_crop": {"WHEAT": 1}}


def test_validate_rejects_missing_columns_and_negative_amounts():
    with pytest.raises(ValueError, match="required columns missing"):
        validate_raw(frame(raw_row()).drop(columns=["L-tonPerHectare"]), "WHEAT")
    with pytest.raises(ValueError, match="negative"):
        validate_raw(frame(raw_row(**{"F-q5200_basalDAP": -5.0})), "WHEAT")


def test_tidy_output_contains_no_raw_survey_columns_as_features():
    out = tidy(frame(raw_row()), "WHEAT", "t")
    assert set(FEATURES) <= set(out.columns)
    assert not [c for c in out.columns if c.startswith(("L-", "I-", "B-", "G-", "F-"))]
