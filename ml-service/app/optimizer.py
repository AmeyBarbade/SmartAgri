"""Fertilizer optimization engine (Milestone 5).

Given the nutrient requirement produced by the backend's deterministic requirement engine (Milestone 4) and the
available fertilizers (grade, price, optional per-product cap), find kg/ha of each product that supplies at least
the required N, P2O5 and K2O. Three candidate plans are solved as linear programs with SciPy ``linprog`` / HiGHS:

    LOWEST_COST   minimise cost;                                                ties -> least excess
    MIN_EXCESS    minimise total nutrient excess;                                ties -> lowest cost
    BALANCED      minimise total excess s.t. cost <= C_A + beta * (C_B - C_A);   ties -> lowest cost
                  (C_A, C_B = cost of the LOWEST_COST and MIN_EXCESS optima; beta = budget share)

This module never invents a requirement (it only satisfies the one it is given), and it does not use the yield
model or an LLM. Every returned plan is re-verified here from the rounded quantities; a plan that fails the check is
never returned. The backend re-checks it again (``FertilizerPlanVerifier``). Formulation, assumptions and tolerances:
docs/OPTIMIZER.md.

CLI:  python -m app.optimizer request.json   (prints the result as JSON)
"""

from __future__ import annotations

import json
import math
import sys
from enum import Enum
from typing import Literal

import numpy as np
import scipy
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from scipy.optimize import linprog

NUTRIENTS = ("n", "p2o5", "k2o")
NUTRIENT_LABELS = {"n": "N", "p2o5": "P2O5", "k2o": "K2O"}

# --- Numerical settings (not agronomy; see docs/OPTIMIZER.md §6) ------------------------------------------------
QUANTITY_STEP_KG_HA = 0.001
"""Returned quantities are rounded to this step (1 g/ha): to the nearest step if every requirement is still met
exactly, otherwise every quantity is rounded up (which can only add nutrients)."""
REQUIREMENT_MARGIN_KG_HA = 1e-6
"""The LP is solved for requirement + margin so the solver's feasibility tolerance (1e-9) can never leave a deficit."""
DUAL_ZERO_REL = 1e-7
"""Reduced costs / duals below this (relative to the largest objective coefficient) count as zero when the
tie-break stage is restricted to the optimal face."""
BUDGET_SLACK_REL = 1e-9
"""Relative slack on the BALANCED cost budget, used only if HiGHS reports the exact budget infeasible (round-off)."""
ROUNDING_GUARD = 1e-6
"""Solver noise below 1e-6 of a step (1e-9 kg/ha) is not rounded up to a whole extra step."""
SOLVER_METHOD = "highs-ds"  # HiGHS dual simplex: returns a vertex, deterministic for identical input
SOLVER_OPTIONS = {"presolve": True, "primal_feasibility_tolerance": 1e-9, "dual_feasibility_tolerance": 1e-9}
MAX_REQUIREMENT_KG_HA = 1000.0
MAX_AREA_HA = 1e7  # fields.area_ha is DECIMAL(10,3)

# --- Prototype tuning parameters (defaults; overridable per request) ---------------------------------------------
DEFAULT_MAX_KG_HA = 500.0
DEFAULT_BALANCED_BUDGET_SHARE = 0.5  # share of the MIN_EXCESS cost premium the BALANCED plan may spend

DISCLAIMER = (
    "Prototype decision support. Plans satisfy the calculated requirement mathematically; prices are indicative and "
    "the requirement itself must be validated with a soil test and local agronomic advice."
)


class Strategy(str, Enum):
    LOWEST_COST = "LOWEST_COST"
    MIN_EXCESS = "MIN_EXCESS"
    BALANCED = "BALANCED"


class OptimizerError(RuntimeError):
    """The solver or the post-solve verification failed. Never returned to a farmer as a plan."""


# --- Request -----------------------------------------------------------------------------------------------------


class NutrientRequirementKgHa(BaseModel):
    """Nutrients to supply now, kg/ha on the oxide basis (the backend's ``requirementForOptimizer.kgPerHa``)."""

    model_config = ConfigDict(extra="forbid")

    n: float = Field(ge=0, le=MAX_REQUIREMENT_KG_HA, allow_inf_nan=False)
    p2o5: float = Field(ge=0, le=MAX_REQUIREMENT_KG_HA, allow_inf_nan=False)
    k2o: float = Field(ge=0, le=MAX_REQUIREMENT_KG_HA, allow_inf_nan=False)

    def as_array(self) -> np.ndarray:
        return np.array([self.n, self.p2o5, self.k2o], dtype=float)


class FertilizerOption(BaseModel):
    """One available product. Grades and price come from the backend's fertilizer catalogue."""

    model_config = ConfigDict(extra="forbid")

    code: str = Field(min_length=1, max_length=40)
    name: str | None = Field(default=None, max_length=100)
    n_pct: float = Field(ge=0, le=100, allow_inf_nan=False)
    p2o5_pct: float = Field(ge=0, le=100, allow_inf_nan=False)
    k2o_pct: float = Field(ge=0, le=100, allow_inf_nan=False)
    price_per_kg: float = Field(ge=0, le=100_000, allow_inf_nan=False, description="INR per kg of product")
    max_kg_ha: float | None = Field(
        default=None, ge=0, le=5000, allow_inf_nan=False,
        description="Practical cap for this product in one application, kg/ha (default: parameters.default_max_kg_ha)",
    )

    @model_validator(mode="after")
    def _grade_total(self):
        if self.n_pct + self.p2o5_pct + self.k2o_pct > 100:
            raise ValueError("n_pct + p2o5_pct + k2o_pct must not exceed 100")
        return self

    def pct(self, nutrient: str) -> float:
        return getattr(self, f"{nutrient}_pct")


class OptimizerParameters(BaseModel):
    """Prototype tuning parameters. Not agronomic constants (docs/OPTIMIZER.md §5)."""

    model_config = ConfigDict(extra="forbid")

    balanced_budget_share: float = Field(
        default=DEFAULT_BALANCED_BUDGET_SHARE, ge=0, le=1, allow_inf_nan=False,
        description="beta: BALANCED may cost at most C_A + beta * (C_B - C_A)",
    )
    default_max_kg_ha: float = Field(default=DEFAULT_MAX_KG_HA, gt=0, le=5000, allow_inf_nan=False)


class OptimizationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requirement_kg_ha: NutrientRequirementKgHa = Field(
        description="Nutrients due now, kg/ha (N, P2O5, K2O; the backend's requirementForOptimizer.kgPerHa)")
    area_ha: float = Field(gt=0, le=MAX_AREA_HA, allow_inf_nan=False, description="Field area, ha")
    fertilizers: list[FertilizerOption] = Field(
        default_factory=list, max_length=50,
        description="Available products (codes unique). An empty list is valid and gives INFEASIBLE if anything is due")
    parameters: OptimizerParameters = Field(default_factory=OptimizerParameters)

    @field_validator("fertilizers")
    @classmethod
    def _unique_codes(cls, v: list[FertilizerOption]) -> list[FertilizerOption]:
        codes = [f.code for f in v]
        if len(codes) != len(set(codes)):
            raise ValueError("fertilizer codes must be unique")
        return v


# --- Response ----------------------------------------------------------------------------------------------------


class NutrientTotals(BaseModel):
    """N, P2O5 and K2O on the oxide basis. The unit (kg/ha or kg per whole field) is given by the containing field."""

    n: float
    p2o5: float
    k2o: float

    @classmethod
    def of(cls, values) -> NutrientTotals:
        return cls(**{k: float(v) for k, v in zip(NUTRIENTS, values)})


class PlanItem(BaseModel):
    code: str
    name: str | None
    kg_ha: float = Field(description="Product quantity, kg product/ha (rounded to 1 g/ha)")
    field_kg: float = Field(description="Product quantity for the whole field, kg (kg_ha x area_ha)")
    cost_per_ha: float = Field(description="INR/ha")
    field_cost: float = Field(description="INR for the whole field")
    supplied_kg_ha: NutrientTotals = Field(description="Nutrients supplied by this product, kg/ha")
    at_upper_bound: bool = Field(description="True if the quantity equals the product's cap")


class ObjectiveInfo(BaseModel):
    expression: str
    tie_break: str
    value: float = Field(description="Objective recomputed from the returned (rounded) quantities")
    unit: str


class FertilizerPlan(BaseModel):
    strategy: Strategy
    objective: ObjectiveInfo
    items: list[PlanItem] = Field(description="Products with a non-zero quantity, sorted by code")
    supplied_kg_ha: NutrientTotals = Field(description="Total nutrients supplied, kg/ha")
    supplied_field_kg: NutrientTotals = Field(description="Total nutrients supplied, kg for the whole field")
    excess_kg_ha: NutrientTotals = Field(description="Supplied minus required (never negative), kg/ha")
    excess_field_kg: NutrientTotals = Field(description="Excess, kg for the whole field")
    total_excess_kg_ha: float = Field(description="Sum of N, P2O5 and K2O excess, kg/ha")
    cost_per_ha: float = Field(description="Fertilizer cost, INR/ha")
    field_cost: float = Field(description="Fertilizer cost for the whole field, INR")
    total_mass_kg_ha: float = Field(description="Total product mass, kg/ha")
    total_mass_field_kg: float = Field(description="Total product mass for the whole field, kg")
    feasible: bool = Field(description="Re-verified from the returned quantities with no tolerance")
    same_as: list[Strategy] = Field(default_factory=list, description="Other strategies with identical quantities")


class NutrientShortfall(BaseModel):
    nutrient: Literal["N", "P2O5", "K2O"]
    required_kg_ha: float
    max_supply_kg_ha: float
    shortfall_kg_ha: float
    reason: str


class SolverInfo(BaseModel):
    library: str = "scipy.optimize.linprog"
    scipy_version: str = scipy.__version__
    method: str = SOLVER_METHOD
    quantity_step_kg_ha: float = QUANTITY_STEP_KG_HA
    requirement_margin_kg_ha: float = REQUIREMENT_MARGIN_KG_HA


class OptimizationResult(BaseModel):
    status: Literal["OPTIMAL", "NOTHING_REQUIRED", "INFEASIBLE"]
    area_ha: float = Field(description="Field area, ha")
    requirement_kg_ha: NutrientTotals = Field(description="Requirement as received, kg/ha")
    requirement_field_kg: NutrientTotals = Field(description="Requirement for the whole field, kg")
    plans: list[FertilizerPlan] = Field(description="LOWEST_COST, MIN_EXCESS, BALANCED (empty when INFEASIBLE)")
    infeasibility: list[NutrientShortfall] = Field(description="One entry per nutrient that cannot be met")
    warnings: list[str]
    parameters: OptimizerParameters
    solver: SolverInfo = Field(default_factory=SolverInfo)
    disclaimer: str = DISCLAIMER


# --- Plan evaluation (shared by the optimizer, the tests and later the what-if simulator) -----------------------


def evaluate_plan(
    request: OptimizationRequest, strategy: Strategy, quantities_kg_ha: dict[str, float]
) -> FertilizerPlan:
    """Compute every reported number from product quantities and check the hard constraints.

    ``feasible`` is True only if every quantity is finite, within [0, cap], and every nutrient is supplied at
    least as required, compared exactly (no tolerance).
    """
    by_code = {f.code: f for f in request.fertilizers}
    params = request.parameters
    area = request.area_ha
    required = request.requirement_kg_ha.as_array()

    feasible = True
    items: list[PlanItem] = []
    supplied = [0.0, 0.0, 0.0]
    cost = 0.0
    mass = 0.0
    for code in sorted(quantities_kg_ha):
        kg = float(quantities_kg_ha[code])
        fert = by_code.get(code)
        if fert is None:
            raise OptimizerError(f"unknown fertilizer {code}")
        cap = _cap(fert, params)
        if not math.isfinite(kg) or kg < 0 or kg > cap:
            feasible = False
        if kg == 0:
            continue
        item_supply = [kg * fert.pct(nu) / 100 for nu in NUTRIENTS]
        supplied = [s + d for s, d in zip(supplied, item_supply)]
        cost += kg * fert.price_per_kg
        mass += kg
        items.append(PlanItem(
            code=code, name=fert.name, kg_ha=kg, field_kg=kg * area,
            cost_per_ha=kg * fert.price_per_kg, field_cost=kg * fert.price_per_kg * area,
            supplied_kg_ha=NutrientTotals.of(item_supply), at_upper_bound=kg >= cap,
        ))

    if any(s < r for s, r in zip(supplied, required)):
        feasible = False
    excess = [max(0.0, s - r) for s, r in zip(supplied, required)]
    total_excess = sum(excess)
    return FertilizerPlan(
        strategy=strategy,
        objective=_objective_info(strategy, params, cost, total_excess),
        items=items,
        supplied_kg_ha=NutrientTotals.of(supplied),
        supplied_field_kg=NutrientTotals.of([s * area for s in supplied]),
        excess_kg_ha=NutrientTotals.of(excess),
        excess_field_kg=NutrientTotals.of([e * area for e in excess]),
        total_excess_kg_ha=total_excess,
        cost_per_ha=cost,
        field_cost=cost * area,
        total_mass_kg_ha=mass,
        total_mass_field_kg=mass * area,
        feasible=feasible,
    )


def _objective_info(strategy: Strategy, params: OptimizerParameters, cost: float, excess: float):
    if strategy is Strategy.LOWEST_COST:
        return ObjectiveInfo(expression="minimise sum_i price_i * x_i", tie_break="least total excess",
                             value=cost, unit="INR/ha")
    if strategy is Strategy.MIN_EXCESS:
        return ObjectiveInfo(expression="minimise sum_j (supplied_j - required_j)", tie_break="lowest cost",
                             value=excess, unit="kg nutrient/ha")
    return ObjectiveInfo(
        expression=("minimise sum_j (supplied_j - required_j) subject to sum_i price_i * x_i <= "
                    f"C_LOWEST_COST + {params.balanced_budget_share:g} * (C_MIN_EXCESS - C_LOWEST_COST)"),
        tie_break="lowest cost", value=excess, unit="kg nutrient/ha",
    )


# --- Optimizer ---------------------------------------------------------------------------------------------------


def optimize(request: OptimizationRequest) -> OptimizationResult:
    params = request.parameters
    area = request.area_ha
    required = request.requirement_kg_ha.as_array()
    ferts = sorted(request.fertilizers, key=lambda f: f.code)  # input order must not change the answer
    grades = np.array([[f.pct(nu) / 100 for nu in NUTRIENTS] for f in ferts], dtype=float).reshape(-1, 3)
    caps = np.array([_cap(f, params) for f in ferts], dtype=float)

    def result(status, plans=(), infeasibility=(), warnings=()):
        return OptimizationResult(
            status=status, area_ha=area,
            requirement_kg_ha=NutrientTotals.of(required), requirement_field_kg=NutrientTotals.of(required * area),
            plans=list(plans), infeasibility=list(infeasibility), warnings=list(warnings), parameters=params,
        )

    if not (required > 0).any():
        plans = [evaluate_plan(request, s, {}) for s in Strategy]
        _mark_identical(plans)
        return result("NOTHING_REQUIRED", plans, warnings=["No nutrient is due now: every plan applies nothing."])

    max_supply = grades.T @ caps if len(ferts) else np.zeros(3)
    shortfalls = _shortfalls(ferts, required, max_supply, params)
    if shortfalls:
        return result("INFEASIBLE", infeasibility=shortfalls,
                      warnings=["No plan can meet the requirement with the available fertilizers and limits."])

    lp = _LinearProgram(grades, caps, required, max_supply)
    price = np.array([f.price_per_kg for f in ferts])
    excess = grades.sum(axis=1)  # sum_j a_ij: total excess = excess . x - sum_j r_j (constant dropped)
    solutions = {
        Strategy.LOWEST_COST: lp.solve_lexicographic(price, excess),
        Strategy.MIN_EXCESS: lp.solve_lexicographic(excess, price),
    }
    cost_a, cost_b = (float(price @ solutions[s]) for s in (Strategy.LOWEST_COST, Strategy.MIN_EXCESS))
    budget = cost_a + params.balanced_budget_share * max(0.0, cost_b - cost_a)
    # beta * x_B + (1 - beta) * x_A meets this budget exactly, so the LP is feasible up to floating-point round-off
    try:
        solutions[Strategy.BALANCED] = lp.solve_lexicographic(excess, price, extra_row=price, extra_limit=budget)
    except OptimizerError:
        solutions[Strategy.BALANCED] = lp.solve_lexicographic(
            excess, price, extra_row=price, extra_limit=budget + BUDGET_SLACK_REL * max(1.0, budget))

    plans, warnings = [], []
    for strategy, x_lp in solutions.items():
        # first of: nearest gram; round up but drop sub-half-gram solver specks; round everything up
        # that still meets every requirement exactly
        for rounding in ROUNDINGS:
            x = rounding(x_lp, caps)
            plan = evaluate_plan(request, strategy, {f.code: float(q) for f, q in zip(ferts, x) if q > 0})
            if plan.feasible:
                break
        else:  # defensive: margin + round-up make this unreachable, but a violating plan is never returned
            raise OptimizerError(f"{strategy.value} plan failed verification after rounding")
        plans.append(plan)
    _mark_identical(plans)

    for fert in ferts:
        capped = [p.strategy.value for p in plans if any(i.code == fert.code and i.at_upper_bound for i in p.items)]
        if capped:
            warnings.append(f"{fert.code} limited by the per-product upper bound ({_cap(fert, params):g} kg/ha) "
                            f"in {', '.join(capped)}.")
    unavoidable = next(p for p in plans if p.strategy is Strategy.MIN_EXCESS).excess_kg_ha
    for nu in NUTRIENTS:
        if getattr(unavoidable, nu) >= 0.01:  # 10 g/ha; below that it is rounding, not a real excess
            warnings.append(
                f"Every plan over-supplies {NUTRIENT_LABELS[nu]} by at least {getattr(unavoidable, nu):.2f} kg/ha: "
                f"with the available products and upper bounds the requirement cannot be met without it."
            )
    return result("OPTIMAL", plans, warnings=warnings)


class _LinearProgram:
    """min c.x  s.t.  A^T x >= r + margin (required nutrients only),  0 <= x <= ub."""

    def __init__(self, grades: np.ndarray, caps: np.ndarray, required: np.ndarray, max_supply: np.ndarray):
        rows = required > 0
        # margin keeps the solver's tolerance on the safe side; it shrinks only when caps leave no room for it
        margin = np.minimum(REQUIREMENT_MARGIN_KG_HA, (max_supply - required) / 2)[rows]
        target = required[rows] + margin
        self.a_ub = -grades.T[rows]
        self.b_ub = -target
        # Dominance bound (derived, not agronomic): once product i alone covers every required nutrient it contains,
        # more of it only adds cost and excess; every objective and the BALANCED budget have coefficients >= 0, so the
        # bound never removes an optimum. It also pins products that carry no required nutrient to 0.
        dominance = np.zeros(len(caps))
        for j, r in zip(np.flatnonzero(rows), target):
            with np.errstate(divide="ignore"):
                need = np.where(grades[:, j] > 0, r / np.where(grades[:, j] > 0, grades[:, j], 1), 0)
            dominance = np.maximum(dominance, need)
        self.bounds = list(zip(np.zeros(len(caps)), np.minimum(caps, dominance)))

    def solve_lexicographic(self, primary: np.ndarray, tie_break: np.ndarray,
                            extra_row: np.ndarray | None = None, extra_limit: float | None = None) -> np.ndarray:
        """Minimise ``primary``; among its optima minimise ``tie_break``.

        Stage 2 is restricted to the optimal face of stage 1 by complementary slackness with the stage-1 duals: a
        variable with a non-zero reduced cost is fixed at that bound and a constraint with a non-zero dual becomes an
        equality. Unlike adding ``primary . x <= optimum + tol``, this adds no slanted cut, so the answer stays a
        vertex of the original problem (no tiny spurious quantities).
        """
        a_ub, b_ub = self.a_ub, self.b_ub
        if extra_row is not None:
            a_ub, b_ub = np.vstack([a_ub, extra_row]), np.append(b_ub, extra_limit)
        first = self._solve(primary, a_ub, b_ub, self.bounds)
        zero = DUAL_ZERO_REL * max(1.0, float(np.max(np.abs(primary))))
        bounds = [
            (lo, lo) if dl > zero else (hi, hi) if du < -zero else (lo, hi)
            for (lo, hi), dl, du in zip(self.bounds, first.lower.marginals, first.upper.marginals)
        ]
        tight = first.ineqlin.marginals < -zero
        second = self._solve(tie_break, a_ub[~tight], b_ub[~tight], bounds,
                             a_eq=a_ub[tight], b_eq=b_ub[tight], allow_failure=True)
        return (second if second is not None else first).x

    def _solve(self, c, a_ub, b_ub, bounds, a_eq=None, b_eq=None, allow_failure=False):
        if len(a_ub) == 0:
            a_ub = b_ub = None
        if a_eq is not None and len(a_eq) == 0:
            a_eq = b_eq = None
        res = linprog(c, A_ub=a_ub, b_ub=b_ub, A_eq=a_eq, b_eq=b_eq, bounds=bounds,
                      method=SOLVER_METHOD, options=SOLVER_OPTIONS)
        if res.status != 0:
            if allow_failure:
                return None
            raise OptimizerError(f"HiGHS did not find an optimum: {res.message}")
        return res


def _to_grams(steps: np.ndarray, caps: np.ndarray) -> np.ndarray:
    return np.minimum(np.maximum(steps, 0) / round(1 / QUANTITY_STEP_KG_HA), caps) + 0.0  # + 0.0 drops -0.0


def _round_nearest(x: np.ndarray, caps: np.ndarray) -> np.ndarray:
    return _to_grams(np.round(x / QUANTITY_STEP_KG_HA), caps)


def _round_up_drop_specks(x: np.ndarray, caps: np.ndarray) -> np.ndarray:
    steps = x / QUANTITY_STEP_KG_HA
    return _to_grams(np.where(steps < 0.5, 0, np.ceil(steps - ROUNDING_GUARD)), caps)


def _round_up(x: np.ndarray, caps: np.ndarray) -> np.ndarray:
    return _to_grams(np.ceil(x / QUANTITY_STEP_KG_HA - ROUNDING_GUARD), caps)


ROUNDINGS = (_round_nearest, _round_up_drop_specks, _round_up)


def _cap(fert: FertilizerOption, params: OptimizerParameters) -> float:
    return fert.max_kg_ha if fert.max_kg_ha is not None else params.default_max_kg_ha


def _shortfalls(ferts, required, max_supply, params) -> list[NutrientShortfall]:
    """Exact feasibility test: all grades are >= 0, so the requirement is reachable iff x = caps reaches it."""
    out = []
    for j, nu in enumerate(NUTRIENTS):
        if required[j] <= max_supply[j]:
            continue
        sources = [f for f in ferts if f.pct(nu) > 0]
        if not ferts:
            reason = "no fertilizers are available"
        elif not sources:
            reason = f"no available fertilizer contains {NUTRIENT_LABELS[nu]}"
        else:
            limits = ", ".join(f"{f.code} <= {_cap(f, params):g} kg/ha" for f in sources)
            reason = f"per-product upper bounds ({limits}) limit {NUTRIENT_LABELS[nu]} supply"
        out.append(NutrientShortfall(
            nutrient=NUTRIENT_LABELS[nu], required_kg_ha=float(required[j]), max_supply_kg_ha=float(max_supply[j]),
            shortfall_kg_ha=float(required[j] - max_supply[j]), reason=reason,
        ))
    return out


def _mark_identical(plans: list[FertilizerPlan]) -> None:
    for plan in plans:
        key = [(i.code, i.kg_ha) for i in plan.items]
        plan.same_as = [p.strategy for p in plans if p is not plan and [(i.code, i.kg_ha) for i in p.items] == key]


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print("usage: python -m app.optimizer request.json", file=sys.stderr)
        return 2
    with open(argv[0], encoding="utf-8") as fh:
        request = OptimizationRequest.model_validate(json.load(fh))
    print(optimize(request).model_dump_json(indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
