"""Fertilizer optimizer (Milestone 5). Every test runs the real HiGHS solver; nothing is mocked."""

from __future__ import annotations

import json
import random
import re
import subprocess
import sys

import numpy as np
import pytest
from pydantic import ValidationError
from scipy.optimize import linprog

from app.optimizer import (
    NUTRIENTS,
    QUANTITY_STEP_KG_HA,
    OptimizationRequest,
    Strategy,
    evaluate_plan,
    optimize,
)
from tests.optimizer_contract import CONTRACT_FILE, V2_FERTILIZERS, V2_MIGRATION, only, render, request


def plans_by_strategy(result):
    return {p.strategy: p for p in result.plans}


def quantities(plan) -> dict[str, float]:
    return {i.code: i.kg_ha for i in plan.items}


def assert_meets_requirement_exactly(req: OptimizationRequest, plan):
    """Independent re-check from the returned quantities only, with no tolerance at all."""
    grades = {f.code: f for f in req.fertilizers}
    for nu in NUTRIENTS:
        supplied = sum(i.kg_ha * grades[i.code].pct(nu) / 100 for i in plan.items)
        assert supplied >= getattr(req.requirement_kg_ha, nu), (plan.strategy, nu)
    for item in plan.items:
        cap = grades[item.code].max_kg_ha or req.parameters.default_max_kg_ha
        assert 0 < item.kg_ha <= cap
    assert plan.feasible


# --- basic requirements -----------------------------------------------------------------------------------------


def test_n_only_requirement_uses_urea_alone():
    req = request(40, 0, 0, area_ha=2.0)
    result = optimize(req)

    assert result.status == "OPTIMAL"
    for plan in result.plans:
        assert quantities(plan) == {"UREA": 86.957}  # 40 / 0.46 = 86.9565... -> nearest gram that still covers 40
        assert plan.supplied_kg_ha.n == pytest.approx(40.00022)
        assert plan.supplied_kg_ha.p2o5 == 0 and plan.supplied_kg_ha.k2o == 0
        assert plan.items[0].field_kg == pytest.approx(173.914)
        assert_meets_requirement_exactly(req, plan)
    assert set(plans_by_strategy(result)[Strategy.LOWEST_COST].same_as) == {Strategy.MIN_EXCESS, Strategy.BALANCED}


def test_only_one_nutrient_required_never_adds_products_for_other_nutrients():
    for n, p, k, expected in [(0, 30, 0, {"SSP"}), (0, 0, 30, {"MOP"})]:
        result = optimize(request(n, p, k))
        assert set(quantities(plans_by_strategy(result)[Strategy.MIN_EXCESS])) == expected


def test_overlapping_sources_count_nitrogen_from_dap():
    req = request(60, 60, 0, fertilizers=only("UREA", "DAP"))
    plan = plans_by_strategy(optimize(req))[Strategy.LOWEST_COST]

    dap = 60 / 0.46
    urea = (60 - 0.18 * dap) / 0.46  # DAP already supplies 23.48 kg N, urea only tops up
    assert quantities(plan)["DAP"] == pytest.approx(dap, abs=QUANTITY_STEP_KG_HA)
    assert quantities(plan)["UREA"] == pytest.approx(urea, abs=QUANTITY_STEP_KG_HA)
    assert plan.items[0].supplied_kg_ha.n == pytest.approx(0.18 * quantities(plan)["DAP"])
    assert plan.total_excess_kg_ha < 0.001
    assert_meets_requirement_exactly(req, plan)


def test_balanced_npk_requirement_matches_an_independent_solve():
    """M4 example 3 (wheat, low soil): 100 / 75 / 50 kg/ha."""
    req = request(100, 75, 50, area_ha=2.0)
    plans = plans_by_strategy(optimize(req))
    lowest = plans[Strategy.LOWEST_COST]

    grades = np.array([[f["n_pct"], f["p2o5_pct"], f["k2o_pct"]] for f in V2_FERTILIZERS]).T / 100
    prices = [f["price_per_kg"] for f in V2_FERTILIZERS]
    reference = linprog(prices, A_ub=-grades, b_ub=[-100, -75, -50], method="highs-ipm")  # other HiGHS algorithm
    assert lowest.cost_per_ha == pytest.approx(reference.fun, abs=0.05)  # 1 g/ha rounding of <= 5 products
    assert quantities(lowest) == {"DAP": 54.348, "NPK_10_26_26": 192.308, "UREA": 154.319}
    straight = {"DAP": 163.044, "MOP": 83.334, "UREA": 153.592}  # a hand-built plan from straight products only
    assert evaluate_plan(req, Strategy.LOWEST_COST, straight).feasible
    assert lowest.cost_per_ha < evaluate_plan(req, Strategy.LOWEST_COST, straight).cost_per_ha
    for plan in plans.values():
        assert_meets_requirement_exactly(req, plan)


# --- multi-fertilizer combinations and the three objectives ----------------------------------------------------


def test_three_strategies_differ_when_cost_and_excess_conflict():
    """M4 example 6: only P2O5 and K2O are due, but the cheap P sources (DAP, NPK) also carry N."""
    req = request(0, 60, 40)
    result = optimize(req)
    plans = plans_by_strategy(result)
    lowest, least, balanced = plans[Strategy.LOWEST_COST], plans[Strategy.MIN_EXCESS], plans[Strategy.BALANCED]

    assert quantities(lowest) == {"DAP": 43.479, "NPK_10_26_26": 153.847}
    assert lowest.excess_kg_ha.n == pytest.approx(23.21, abs=0.01)
    assert quantities(least) == {"MOP": 66.667, "SSP": 375.0}
    assert least.total_excess_kg_ha < 0.001
    assert lowest.cost_per_ha < balanced.cost_per_ha < least.cost_per_ha
    assert least.total_excess_kg_ha < balanced.total_excess_kg_ha < lowest.total_excess_kg_ha

    # BALANCED spends at most half the premium (+ gram rounding) ...
    budget = lowest.cost_per_ha + 0.5 * (least.cost_per_ha - lowest.cost_per_ha)
    assert balanced.cost_per_ha <= budget + 0.05
    # ... and by convexity (0.5 x_A + 0.5 x_B is feasible) must remove at least half of the cheapest plan's excess
    assert balanced.total_excess_kg_ha <= 0.5 * lowest.total_excess_kg_ha + 0.01
    for plan in result.plans:
        assert_meets_requirement_exactly(req, plan)
        assert plan.same_as == []
    assert not any("over-supplies" in w for w in result.warnings)


def test_balanced_budget_share_endpoints():
    at_zero = plans_by_strategy(optimize(request(0, 60, 40, balanced_budget_share=0)))
    assert quantities(at_zero[Strategy.BALANCED]) == quantities(at_zero[Strategy.LOWEST_COST])
    at_one = plans_by_strategy(optimize(request(0, 60, 40, balanced_budget_share=1)))
    assert quantities(at_one[Strategy.BALANCED]) == quantities(at_one[Strategy.MIN_EXCESS])


def test_unavoidable_excess_is_reported():
    req = request(0, 60, 0, fertilizers=only("DAP", "NPK_10_26_26"))  # every P source contains N
    result = optimize(req)
    least = plans_by_strategy(result)[Strategy.MIN_EXCESS]
    assert least.excess_kg_ha.n > 20
    assert any("over-supplies N" in w for w in result.warnings)


def test_ties_are_broken_by_the_secondary_objective():
    # two identical-grade urea products at different prices: least excess ties, cost must decide
    cheap = {"code": "UREA_B", "name": "Urea (other brand)", "n_pct": 46, "p2o5_pct": 0, "k2o_pct": 0,
             "price_per_kg": 5.50}
    plan = plans_by_strategy(optimize(request(40, 0, 0, fertilizers=only("UREA") + [cheap])))[Strategy.MIN_EXCESS]
    assert quantities(plan) == {"UREA_B": 86.957}


# --- zero, infeasible, empty ------------------------------------------------------------------------------------


def test_zero_requirement_returns_empty_plans():
    result = optimize(request(0, 0, 0, area_ha=3))
    assert result.status == "NOTHING_REQUIRED"
    assert len(result.plans) == 3
    for plan in result.plans:
        assert plan.items == [] and plan.cost_per_ha == 0 and plan.total_mass_kg_ha == 0 and plan.feasible


def test_missing_nutrient_source_is_infeasible_with_a_reason():
    result = optimize(request(40, 30, 20, fertilizers=only("UREA", "DAP")))
    assert result.status == "INFEASIBLE"
    assert result.plans == []
    [shortfall] = result.infeasibility
    assert shortfall.nutrient == "K2O"
    assert shortfall.max_supply_kg_ha == 0 and shortfall.shortfall_kg_ha == 20
    assert "no available fertilizer contains K2O" in shortfall.reason


def test_no_available_fertilizers():
    result = optimize(request(40, 30, 0, fertilizers=[]))
    assert result.status == "INFEASIBLE"
    assert [s.nutrient for s in result.infeasibility] == ["N", "P2O5"]
    assert all(s.reason == "no fertilizers are available" for s in result.infeasibility)
    assert optimize(request(0, 0, 0, fertilizers=[])).status == "NOTHING_REQUIRED"


# --- upper bounds -----------------------------------------------------------------------------------------------


def test_default_upper_bound_makes_large_ssp_only_requirement_infeasible():
    result = optimize(request(0, 100, 0, fertilizers=only("SSP")))
    assert result.status == "INFEASIBLE"
    [shortfall] = result.infeasibility
    assert shortfall.max_supply_kg_ha == pytest.approx(80)  # 500 kg/ha x 16 %
    assert "SSP <= 500 kg/ha" in shortfall.reason


def test_per_product_upper_bound_can_be_raised():
    req = request(0, 100, 0, fertilizers=only("SSP", SSP={"max_kg_ha": 700}))
    plan = plans_by_strategy(optimize(req))[Strategy.LOWEST_COST]
    assert quantities(plan) == {"SSP": 625.0}
    assert_meets_requirement_exactly(req, plan)


def test_binding_upper_bound_shifts_supply_to_another_product():
    req = request(90, 0, 0, fertilizers=only("UREA", "DAP", "SSP", UREA={"max_kg_ha": 100}))
    result = optimize(req)
    plan = plans_by_strategy(result)[Strategy.LOWEST_COST]
    assert quantities(plan)["UREA"] == 100
    assert quantities(plan)["DAP"] == pytest.approx((90 - 46) / 0.18, abs=QUANTITY_STEP_KG_HA)
    assert next(i for i in plan.items if i.code == "UREA").at_upper_bound
    assert "UREA limited by the per-product upper bound (100 kg/ha) in LOWEST_COST, MIN_EXCESS, BALANCED." in result.warnings
    assert_meets_requirement_exactly(req, plan)


def test_requirement_exactly_at_the_cap_is_feasible_and_just_above_is_not():
    at_cap = optimize(request(46, 0, 0, fertilizers=only("UREA", UREA={"max_kg_ha": 100})))
    assert at_cap.status == "OPTIMAL"
    assert quantities(at_cap.plans[0]) == {"UREA": 100.0}
    above = optimize(request(46.0001, 0, 0, fertilizers=only("UREA", UREA={"max_kg_ha": 100})))
    assert above.status == "INFEASIBLE"
    assert above.infeasibility[0].shortfall_kg_ha == pytest.approx(0.0001)


def test_zero_cap_marks_a_product_unavailable():
    plan = optimize(request(40, 0, 0, fertilizers=only("UREA", "DAP", UREA={"max_kg_ha": 0}))).plans[0]
    assert set(quantities(plan)) == {"DAP"}


# --- numerical tolerance ----------------------------------------------------------------------------------------


@pytest.mark.parametrize("n,p,k", [
    (0.1 + 0.2, 0.3, 0.7),
    (1e-9, 0, 0),
    (1e-7, 1e-7, 1e-7),
    (33.333333333333336, 16.666666666666668, 0),
    (999.999, 0, 0),
    (46 * 0.3, 46 * 0.7, 60 * 0.9),
])
def test_awkward_floating_point_requirements_are_never_undersupplied(n, p, k):
    req = request(n, p, k, fertilizers=only(*[f["code"] for f in V2_FERTILIZERS], UREA={"max_kg_ha": 5000}))
    result = optimize(req)
    assert result.status == "OPTIMAL"
    for plan in result.plans:
        assert_meets_requirement_exactly(req, plan)


def test_random_requirements_always_meet_hard_constraints():
    rng = random.Random(20260927)
    for _ in range(150):
        req = request(
            rng.choice([0, rng.uniform(0, 200)]), rng.choice([0, rng.uniform(0, 120)]),
            rng.choice([0, rng.uniform(0, 120)]), area_ha=10 ** rng.uniform(-3, 5),
        )
        for plan in optimize(req).plans:
            assert_meets_requirement_exactly(req, plan)
            for item in plan.items:  # quantities are whole grams per hectare
                assert abs(item.kg_ha / QUANTITY_STEP_KG_HA - round(item.kg_ha / QUANTITY_STEP_KG_HA)) < 1e-6
            assert plan.total_excess_kg_ha >= 0


# --- reported numbers -------------------------------------------------------------------------------------------


def test_cost_mass_and_field_totals():
    req = request(67.5, 45, 22.5, area_ha=0.5)  # M4 example 4 (rice, 0.5 ha)
    by_code = {f["code"]: f for f in V2_FERTILIZERS}
    for plan in optimize(req).plans:
        cost = sum(i.kg_ha * by_code[i.code]["price_per_kg"] for i in plan.items)
        assert plan.cost_per_ha == pytest.approx(cost, rel=1e-12)
        assert plan.field_cost == pytest.approx(cost * 0.5, rel=1e-12)
        assert plan.total_mass_kg_ha == pytest.approx(sum(i.kg_ha for i in plan.items), rel=1e-12)
        assert plan.total_mass_field_kg == pytest.approx(plan.total_mass_kg_ha * 0.5, rel=1e-12)
        for item in plan.items:
            assert item.field_kg == pytest.approx(item.kg_ha * 0.5, rel=1e-12)
            assert item.cost_per_ha == pytest.approx(item.kg_ha * by_code[item.code]["price_per_kg"], rel=1e-12)
    lowest = plans_by_strategy(optimize(req))[Strategy.LOWEST_COST]
    # 48.914 x 27.00 + 86.539 x 29.40 + 108.787 x 5.92
    assert lowest.cost_per_ha == pytest.approx(1320.678 + 2544.2466 + 644.01904, abs=1e-9)


def test_excess_is_supply_minus_requirement():
    req = request(0, 60, 40)
    plan = plans_by_strategy(optimize(req))[Strategy.LOWEST_COST]
    # DAP 43.479 kg + NPK 153.847 kg
    supplied_n = 43.479 * 0.18 + 153.847 * 0.10
    supplied_p = 43.479 * 0.46 + 153.847 * 0.26
    supplied_k = 153.847 * 0.26
    assert plan.excess_kg_ha.n == pytest.approx(supplied_n, abs=1e-9)
    assert plan.excess_kg_ha.p2o5 == pytest.approx(supplied_p - 60, abs=1e-9)
    assert plan.excess_kg_ha.k2o == pytest.approx(supplied_k - 40, abs=1e-9)
    assert plan.total_excess_kg_ha == pytest.approx(supplied_n + supplied_p - 60 + supplied_k - 40, abs=1e-9)
    assert plan.objective.value == plan.cost_per_ha


def test_evaluate_plan_rejects_violations():
    req = request(40, 0, 0, fertilizers=only("UREA", UREA={"max_kg_ha": 100}))
    assert evaluate_plan(req, Strategy.LOWEST_COST, {"UREA": 86.957}).feasible
    assert not evaluate_plan(req, Strategy.LOWEST_COST, {"UREA": 86.956}).feasible  # 39.99976 kg N
    assert not evaluate_plan(req, Strategy.LOWEST_COST, {"UREA": 100.001}).feasible  # above the cap
    assert not evaluate_plan(req, Strategy.LOWEST_COST, {"UREA": -1}).feasible
    assert not evaluate_plan(req, Strategy.LOWEST_COST, {"UREA": float("nan")}).feasible


# --- scale and determinism --------------------------------------------------------------------------------------


@pytest.mark.parametrize("area", [0.0001, 0.001, 1.0, 5_000.0, 9_999_999.999])
def test_field_size_only_scales_the_per_hectare_plan(area):
    base = optimize(request(120, 60, 40, area_ha=1.0))
    scaled = optimize(request(120, 60, 40, area_ha=area))
    for a, b in zip(base.plans, scaled.plans):
        assert quantities(a) == quantities(b)
        for item in b.items:
            assert item.field_kg == pytest.approx(item.kg_ha * area, rel=1e-12)
        assert b.supplied_field_kg.n == pytest.approx(b.supplied_kg_ha.n * area, rel=1e-12)
        assert b.field_cost == pytest.approx(b.cost_per_ha * area, rel=1e-12)
    assert scaled.requirement_field_kg.n == pytest.approx(120 * area, rel=1e-12)


def test_results_are_deterministic_and_independent_of_catalogue_order():
    first = optimize(request(100, 75, 50)).model_dump()
    assert optimize(request(100, 75, 50)).model_dump() == first
    shuffled = list(V2_FERTILIZERS)
    random.Random(7).shuffle(shuffled)
    assert optimize(request(100, 75, 50, fertilizers=shuffled)).model_dump() == first
    assert optimize(request(100, 75, 50, fertilizers=list(reversed(V2_FERTILIZERS)))).model_dump() == first


# --- input validation -------------------------------------------------------------------------------------------


@pytest.mark.parametrize("change", [
    {"requirement_kg_ha": {"n": -1, "p2o5": 0, "k2o": 0}},
    {"requirement_kg_ha": {"n": float("nan"), "p2o5": 0, "k2o": 0}},
    {"requirement_kg_ha": {"n": 1001, "p2o5": 0, "k2o": 0}},
    {"requirement_kg_ha": {"n": 1, "p2o5": 0}},
    {"area_ha": 0},
    {"area_ha": -2},
    {"area_ha": float("inf")},
    {"fertilizers": [V2_FERTILIZERS[0], V2_FERTILIZERS[0]]},
    {"fertilizers": [{**V2_FERTILIZERS[0], "n_pct": 60, "p2o5_pct": 50}]},
    {"fertilizers": [{**V2_FERTILIZERS[0], "price_per_kg": -1}]},
    {"fertilizers": [{**V2_FERTILIZERS[0], "max_kg_ha": -5}]},
    {"fertilizers": [{**V2_FERTILIZERS[0], "code": ""}]},
    {"parameters": {"balanced_budget_share": 1.5}},
    {"parameters": {"default_max_kg_ha": 0}},
    {"yield_model": "xgboost"},
])
def test_invalid_requests_are_rejected(change):
    body = {"requirement_kg_ha": {"n": 40, "p2o5": 0, "k2o": 0}, "area_ha": 1, "fertilizers": V2_FERTILIZERS}
    with pytest.raises(ValidationError):
        OptimizationRequest.model_validate({**body, **change})


# --- boundaries of the component --------------------------------------------------------------------------------


def test_optimizer_does_not_load_the_yield_model_or_ml_libraries():
    code = ("import sys, app.optimizer; "
            "print(sorted(m for m in sys.modules if m.split('.')[0] in "
            "{'xgboost', 'sklearn', 'joblib', 'app'} and m != 'app.optimizer' and m != 'app'))")
    out = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, check=True).stdout
    assert out.strip() == "[]"


def test_test_catalogue_matches_backend_reference_data():
    sql = V2_MIGRATION.read_text(encoding="utf-8")
    rows = re.findall(r"\('([A-Z0-9_]+)',\s*'([^']+)',\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),", sql)
    from_sql = {code: (name, float(n), float(p), float(k), float(price)) for code, name, n, p, k, price in rows}
    ours = {f["code"]: (f["name"], f["n_pct"], f["p2o5_pct"], f["k2o_pct"], f["price_per_kg"]) for f in V2_FERTILIZERS}
    assert ours == from_sql


def test_backend_contract_fixture_is_current_solver_output():
    """The Java verifier test reads this file; it must be exactly what the optimizer produces today."""
    assert CONTRACT_FILE.exists(), "run: python -m tests.optimizer_contract"
    assert json.loads(CONTRACT_FILE.read_text(encoding="utf-8")) == json.loads(render()), \
        "optimizer output changed: review it, then run `python -m tests.optimizer_contract`"
