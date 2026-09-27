from datetime import date

import pytest

from app.features import (
    FEATURES, LEAKAGE_EXCLUDED, SCENARIO_NUMERIC, build_preprocessor, normalise_previous_crop, normalise_state,
    nutrients_from_products, sowing_day, to_frame,
)


def test_nutrients_from_straight_products():
    n, p, k = nutrients_from_products({"UREA": 100, "DAP": 100, "MOP": 50})
    assert n == pytest.approx(46 + 18)
    assert p == pytest.approx(46)
    assert k == pytest.approx(30)


def test_nutrients_from_complex_grade():
    n, p, k = nutrients_from_products({"NPK": 200}, complex_grades={"NPK": (10, 26, 26)})
    assert (n, p, k) == pytest.approx((20, 52, 52))


def test_unknown_product_is_rejected():
    with pytest.raises(KeyError):
        nutrients_from_products({"MYSTERY": 10})


@pytest.mark.parametrize("crop, sown, expected", [
    ("WHEAT", date(2017, 11, 15), 45),
    ("WHEAT", date(2018, 1, 5), 96),       # January sowing belongs to the season that started in October
    ("RICE", date(2018, 7, 1), 61),
])
def test_sowing_day(crop, sown, expected):
    assert sowing_day(crop, sown) == expected


@pytest.mark.parametrize("raw, expected", [
    ("UttarPradesh", "UTTAR_PRADESH"), ("Uttar Pradesh", "UTTAR_PRADESH"), ("Chattisgarh", "CHHATTISGARH"),
    ("WestBengal", "WEST_BENGAL"), ("bihar", "BIHAR"),
])
def test_state_normalisation(raw, expected):
    assert normalise_state(raw) == expected


@pytest.mark.parametrize("raw, expected", [
    ("Rice", "RICE"), ("Mungbean", "PULSE"), ("Lentil", "PULSE"), ("Sugarcane", "OTHER"), (None, None),
    ("PULSE", "PULSE"), ("pulse", "PULSE"), ("OTHER", "OTHER"), ("Pulses", "PULSE"),  # canonical values round-trip
])
def test_previous_crop_grouping(raw, expected):
    assert normalise_previous_crop(raw) == expected


def test_to_frame_requires_every_feature():
    with pytest.raises(ValueError, match="missing feature columns"):
        to_frame([{"crop": "WHEAT"}])


def test_preprocessor_tolerates_unseen_category_and_missing_values():
    base = {f: 0.0 for f in FEATURES} | {"crop": "WHEAT", "state": "BIHAR", "soil_texture": "MEDIUM",
                                          "variety_type": "IMPROVED", "previous_crop": "RICE"}
    train = to_frame([base, base | {"crop": "RICE", "n_kg_ha": 100.0}])
    pre = build_preprocessor(scale_numeric=True).fit(train)
    probe = to_frame([base | {"state": "KERALA", "variety_type": None, "sowing_day": None}])
    assert pre.transform(probe).shape[1] == pre.transform(train).shape[1]


def test_no_feature_is_a_post_application_or_post_harvest_variable():
    forbidden_words = ("yield", "harvest", "duration", "lodg", "drought", "flood", "price", "irrig_times",
                       "weed", "severity", "production")
    for feature in FEATURES:
        assert not any(w in feature.lower() for w in forbidden_words), feature
        assert feature not in LEAKAGE_EXCLUDED


def test_scenario_features_are_the_nutrient_doses():
    assert SCENARIO_NUMERIC == ["n_kg_ha", "p2o5_kg_ha", "k2o_kg_ha"]
