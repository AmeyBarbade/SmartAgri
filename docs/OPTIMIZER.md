# AgriOptima — Fertilizer Optimization Engine (Milestone 5)

> Prototype decision support. The optimizer only decides **how** to supply a nutrient requirement with the
> available products. It never decides **how much** nutrient a crop needs; that comes from the deterministic
> requirement engine (Milestone 4, [RECOMMENDATION_ENGINE.md](RECOMMENDATION_ENGINE.md)).

Code: `ml-service/app/optimizer.py` (Python, SciPy `linprog` + HiGHS) and
`backend/src/main/java/com/agrioptima/engine/plan/FertilizerPlanVerifier.java` (independent Java re-check).
Every number in this document was produced by the code as committed to the working tree on 2026-09-27.

Status labels used below:

| Label | Meaning |
|---|---|
| **FACT** | Definitional or physical (mass balance, fertilizer grade definitions, the regulatory grade itself). |
| **REFERENCED** | Taken from a cited source already used by the project. |
| **DERIVED** | Follows mathematically from the formulation; no judgement involved. |
| **PROTOTYPE_ASSUMPTION** | A tuning choice made for this prototype. Not a scientific constant; can be changed per request. |
| **NUMERICAL** | A floating-point / solver setting. Not agronomy. |

---

## 1. Scope and boundaries

```
Spring Boot (M4)                                     ml-service (M5)                       Spring Boot (M5, M7)
NutrientRequirementEngine ──requirementForOptimizer──► optimizer.optimize(request) ──plans──► FertilizerPlanVerifier
   kg/ha N, P2O5, K2O due now                          3 LPs (HiGHS), rounding,              recompute from own
                                                       self-verification                     catalogue; reject any
                                                                                              violating plan
```

- **Input**: requirement kg/ha (N, P₂O₅, K₂O), field area, available fertilizers (grade, price, optional cap),
  optional tuning parameters.
- **Output**: up to three plans (`LOWEST_COST`, `MIN_EXCESS`, `BALANCED`) with per-ha and whole-field quantities,
  nutrients supplied, excess, cost, mass, objective value and feasibility. If no plan can exist, the output is an
  `INFEASIBLE` status with the reason for each nutrient.
- **Not used**: the yield model, an LLM, weather, or any rule that changes the requirement. A test
  (`test_optimizer_does_not_load_the_yield_model_or_ml_libraries`) checks that importing the optimizer loads no
  xgboost, sklearn, joblib or `app.model_store`.
- **HTTP**: `POST /optimize` on the ML service (Milestone 6) calls this function unchanged and adds `feasible`,
  `infeasibility_reason` and `units` ([ML_API.md](ML_API.md)). It also runs as a CLI (`python -m app.optimizer request.json`).
- **Not built yet**: the Spring→FastAPI call and plan scoring with yield (Milestone 7), and the what-if simulator
  (Milestone 9).

## 2. Mathematical formulation

**Sets.** *I* = available fertilizers (sorted by code, so that input order cannot change the answer);
*J* = {N, P₂O₅, K₂O}.

**Parameters.**

| Symbol | Meaning | Unit | Status |
|---|---|---|---|
| a<sub>ij</sub> | grade of fertilizer *i* for nutrient *j* ÷ 100 | kg nutrient / kg product | FACT (grade definition); values REFERENCED to FCO 1985 via the V2 catalogue |
| r<sub>j</sub> | requirement due now | kg/ha | from Milestone 4 (the optimizer never changes it) |
| p<sub>i</sub> | price | ₹/kg product | PROTOTYPE_ASSUMPTION (indicative V2 prices) |
| u<sub>i</sub> | per-product cap | kg/ha | PROTOTYPE_ASSUMPTION (default 500; per product `max_kg_ha`) |
| A | field area | ha | user data |

**Decision variables.** x<sub>i</sub> = kg of fertilizer *i* per hectare.

**Derived quantities** (all linear in x; FACT, a mass balance):

```
supplied_j  s_j(x) = Σ_i a_ij · x_i                      kg/ha
excess_j    e_j(x) = s_j(x) − r_j        (≥ 0 for every feasible plan)
cost        C(x)   = Σ_i p_i · x_i                       ₹/ha
mass        M(x)   = Σ_i x_i                             kg product/ha
total excess E(x)  = Σ_j e_j(x) = Σ_i g_i · x_i − Σ_j r_j,   g_i = Σ_j a_ij
field values       = per-ha value × A
```

**Hard constraints** (for every returned plan):

```
s_j(x) ≥ r_j                 for every j with r_j > 0         (requirement; checked exactly after rounding)
0 ≤ x_i ≤ u_i                for every i                      (no negative or above-cap quantities)
```

In the LP the requirement row is `s_j(x) ≥ r_j + m_j`, where m_j = min(10⁻⁶, (max supply_j − r_j)/2) is a numerical
safety margin (§6). Nutrients with r_j = 0 get no row. Nothing forces their supply to be zero, but it is counted as
excess.

**Dominance bound** (DERIVED). In the LP, x_i ≤ min(u_i, max_{j: r_j>0, a_ij>0} (r_j + m_j)/a_ij), and the bound is 0
if product *i* carries no required nutrient. Beyond that quantity, product *i* alone already covers every required
nutrient it contains. More of it can only add cost and excess, and every objective and constraint below has
non-negative coefficients in x, so the bound never removes an optimum. Its practical effect is that a product with
none of the required nutrients is fixed at 0 (e.g. MOP when only N is due).

**Feasibility** (DERIVED, exact). All a_ij ≥ 0, so a feasible plan exists iff x = u reaches every requirement:
Σ_i a_ij·u_i ≥ r_j for all j. This is checked before calling the solver, so an infeasible request gets a precise
per-nutrient reason instead of a solver status code.

## 3. Objective functions

All three are linear programs over the same feasible set. Each has a **lexicographic tie-break**: among plans that
are optimal for the primary objective, the secondary one decides.

| Plan | Primary objective | Tie-break | Reported `objective.value` |
|---|---|---|---|
| **LOWEST_COST** (A) | minimise C(x) = Σ p_i x_i | least total excess E(x) | C(x), ₹/ha |
| **MIN_EXCESS** (B) | minimise E(x) = Σ_j (s_j − r_j) | lowest cost C(x) | E(x), kg nutrient/ha |
| **BALANCED** (C) | minimise E(x) subject to C(x) ≤ C_A + β·(C_B − C_A) | lowest cost C(x) | E(x), kg nutrient/ha |

C_A and C_B are the (unrounded) costs of the A and B optima; β = `balanced_budget_share`, default 0.5
(**PROTOTYPE_ASSUMPTION**).

**What BALANCED guarantees** (DERIVED, by convexity). The mixture ½·x_A + ½·x_B is feasible, costs exactly
C_A + ½(C_B − C_A) and has total excess ½·E(x_A) + ½·E(x_B). The LP can only do better, so with β = 0.5 the balanced
plan spends at most half of the premium the minimum-excess plan costs over the cheapest plan (up to 1 g/ha rounding),
and it removes at least half of the cheapest plan's excess. β = 0 gives the lowest-cost plan and β = 1 gives the
minimum-excess plan (both tested).

**Why not a weighted sum?** Architecture §7 (Milestone 1) sketched plan C as a weighted objective
`C(x) + λ_e·E(x) + λ_s·M(x)`. This was implemented first with λ_e = ₹20/kg excess and λ_s = ₹0.5/kg product. On all
five Milestone 4 example requirements the weighted plan was **identical to LOWEST_COST**. A weighted LP always
returns a vertex, so it can only switch wholesale between A-like and B-like plans at a λ threshold, and whether it
switches depends entirely on an arbitrary λ. The budget form above has one parameter with a plain meaning ("spend at
most half the extra") and produces a real intermediate plan when A and B differ (§8, example 5).

**Equal weighting of nutrients in E(x)** (PROTOTYPE_ASSUMPTION). One kg of excess N, P₂O₅ or K₂O counts the same.
Their environmental effects differ (N leaching and N₂O; P runoff), but no nutrient-specific weights were found with a
source that could be cited, so none were invented.

**How ties are broken exactly** (NUMERICAL). The tie-break stage is restricted to the *optimal face* of the primary
LP by complementary slackness with the stage-1 duals from HiGHS: a variable with a non-zero reduced cost is fixed at
that bound, and a constraint with a non-zero dual becomes an equality. The naive alternative, adding a row
`primary·x ≤ optimum + tol`, was tried first. It added a slanted cut, and the solver then returned tiny spurious
mixtures (0.001 kg/ha SSP or NPK).

## 4. Constraints and bounds: what is fact and what is a choice

| Item | Value | Status | Notes |
|---|---|---|---|
| Nutrient supplied = kg product × grade % | — | FACT | mass balance; grades are labelled on the oxide basis (P₂O₅, K₂O) |
| Fertilizer grades | Urea 46-0-0, DAP 18-46-0, MOP 0-0-60, NPK 10-26-26, SSP 0-16-0 | REFERENCED | Fertiliser (Control) Order 1985, Sch. I, via Flyway V2; pytest checks the test catalogue against the SQL |
| Supply ≥ requirement | — | hard constraint | requirement is Milestone 4 output |
| Quantity ≥ 0 | — | FACT | enforced by bounds and re-checked |
| Per-product cap | 500 kg/ha default, `max_kg_ha` per product (0 = unavailable) | PROTOTYPE_ASSUMPTION | a sanity limit for one application, not an agronomic maximum. It is high enough for SSP-only P at 75 kg P₂O₅/ha (469 kg). No single-application maximum was found in the sources used in M4 |
| Grade total ≤ 100 % | — | FACT | request validation |
| Prices | V2 indicative ₹/kg (urea 5.92, DAP 27.00, MOP 34.00, NPK 29.40, SSP 11.00) | PROTOTYPE_ASSUMPTION | bag price ÷ bag weight, documented in the V2 migration; editable. They drive LOWEST_COST and the BALANCED budget |
| Nutrient availability / use efficiency | 100 % of labelled nutrient counted | PROTOTYPE_ASSUMPTION | no losses, volatilisation or P fixation modelled; these are part of the M4 dose recommendations |
| Equal weighting of N, P₂O₅, K₂O in excess | 1 : 1 : 1 | PROTOTYPE_ASSUMPTION | see §3 |
| β (BALANCED budget share) | 0.5 | PROTOTYPE_ASSUMPTION | request `parameters.balanced_budget_share` ∈ [0, 1] |
| Requirement / area input range | 0 ≤ r ≤ 1000 kg/ha; 0 < A ≤ 10⁷ ha | validation limit | area limit mirrors `fields.area_ha DECIMAL(10,3)` |

## 5. Tuning parameters (request `parameters`)

| Parameter | Default | Range | Meaning |
|---|---|---|---|
| `balanced_budget_share` | 0.5 | 0–1 | β in §3 |
| `default_max_kg_ha` | 500 | (0, 5000] | cap for products without `max_kg_ha` |

The effective parameters are echoed in every response.

## 6. Solver and numerical tolerance

| Setting | Value | Why |
|---|---|---|
| Solver | `scipy.optimize.linprog`, `method="highs-ds"` (HiGHS dual simplex), SciPy 1.18.1 | exact LP. Dual simplex returns a vertex, so results are deterministic for identical input |
| HiGHS primal / dual feasibility tolerance | 10⁻⁹ / 10⁻⁹ | tighter than the defaults (10⁻⁷) |
| Requirement margin m_j | 10⁻⁶ kg/ha (1 mg/ha) | covers the solver tolerance, so the LP answer is never below r_j. It shrinks to half the headroom when caps leave less than 2·10⁻⁶ |
| Quantity step | 0.001 kg/ha (1 g/ha) | returned quantities are whole grams per ha |
| Rounding rule | the first of: nearest gram; round up but drop < 0.5 g specks; round everything up, whichever meets every requirement **exactly** | rounding up only adds nutrients. Quantities above a cap are clamped to the cap |
| Post-solve check (Python) | `s_j ≥ r_j` with **no tolerance**, `0 ≤ x_i ≤ u_i`, finite | a plan that fails raises `OptimizerError`; it is never returned |
| Re-check (Java) | `s_j ≥ r_j − 10⁻⁹ kg/ha`; claimed totals within 10⁻⁹ relative | the tolerance only absorbs Python-vs-Java summation order (~10⁻¹³) |
| Dual "zero" for the face restriction | 10⁻⁷ × max objective coefficient | see §3 |
| BALANCED budget slack | 0, then 10⁻⁹ relative only if HiGHS reports the exact budget infeasible | round-off in C_A, C_B |

The LP is solved **per hectare**. Field area only multiplies the results, so a 0.0001 ha plot and a 10⁷ ha field get
the same per-ha plan and exactly scaled field totals (tested for 0.0001 to 9,999,999.999 ha). A consequence is that
quantities are *reported* to 1 g/ha. On a 0.001 ha plot that is 1 mg of product per field. Whether a farmer can
measure that is a presentation question for the UI (M8), not something the optimizer decides.

Observed effect of rounding in the examples: excess of 0.0001–0.0006 kg/ha per nutrient (< 1 g/ha).

## 7. Edge cases

| Case | Behaviour | Test |
|---|---|---|
| Zero requirement | `NOTHING_REQUIRED`; three empty plans (cost 0, feasible), warning. No solver call | `test_zero_requirement_returns_empty_plans` |
| One nutrient required | Only products carrying it can be > 0 (dominance bound) | `test_only_one_nutrient_required_…`, `test_n_only_…` |
| Nutrient with no source | `INFEASIBLE`, `"no available fertilizer contains K2O"`, `plans: []` | `test_missing_nutrient_source_…` |
| No fertilizers | `INFEASIBLE` per required nutrient (`"no fertilizers are available"`); `NOTHING_REQUIRED` if nothing is due | `test_no_available_fertilizers` |
| Caps too low | `INFEASIBLE`, max achievable supply, the caps that limit it | `test_default_upper_bound_makes_…` |
| Requirement exactly at the cap limit | feasible (x = cap). 0.0001 kg/ha more → infeasible | `test_requirement_exactly_at_the_cap_…` |
| Binding cap | other products fill the gap; warning names the product and the plans | `test_binding_upper_bound_…` |
| Unavoidable excess | warning with the minimum excess any plan must have | `test_unavoidable_excess_is_reported` |
| Overlapping nutrients | N from DAP and NPK counts toward N | `test_overlapping_sources_…` |
| Awkward floats (0.1+0.2, 10⁻⁹, 1/3, 999.999) | never under-supplied | `test_awkward_floating_point_…` |
| 150 random requirements × 3 plans, areas 10⁻³–10⁵ ha | every plan exactly feasible, whole grams | `test_random_requirements_…` |
| Tiny / huge field | per-ha plan unchanged, field totals scale | `test_field_size_only_scales_…` |
| Invalid input (negative, NaN, ∞, grade > 100 %, duplicate codes, unknown fields…) | pydantic `ValidationError` → HTTP 422 problem+json on `/optimize` (M6) | `test_invalid_requests_are_rejected` |
| Input order of fertilizers | identical output | `test_results_are_deterministic_…` |

## 8. Examples (actual output)

Requirements are the Milestone 4 worked examples (RECOMMENDATION_ENGINE.md §5) plus edge cases. The catalogue is the
V2 reference data with the default caps. Request files are in `ml-service/examples/optimizer/`, and all cases are in
`backend/src/test/resources/optimizer/optimizer-contract.json`.

```bash
scripts/ml.sh optimize examples/optimizer/m4_6_wheat_tillering_pk_only_1ha.json      # or .\scripts\ml.ps1 optimize
```

| # | Case | Req. N/P₂O₅/K₂O kg/ha | Area | Plan | Products kg/ha | Excess N/P₂O₅/K₂O kg/ha | Cost ₹/ha | Field cost ₹ |
|---|---|---|---|---|---|---|---|---|
| 1 | Wheat CRI (M4 #1) | 40 / 0 / 0 | 2 ha | all three identical | Urea 86.957 | 0.0002 / 0 / 0 | 514.79 | 1,029.57 |
| 2 | Wheat, low soil (M4 #3) | 100 / 75 / 50 | 2 ha | all three identical | DAP 54.348, NPK 192.308, Urea 154.319 | 0.0002 / 0.0002 / 0.0001 | 8,034.82 | 16,069.64 |
| 3 | Rice PI (M4 #4) | 67.5 / 45 / 22.5 | 0.5 ha | all three identical | DAP 48.914, NPK 86.539, Urea 108.787 | 0.0004 / 0.0006 / 0.0001 | 4,508.94 | 2,254.47 |
| 4 | Maize rainfed (M4 #5) | 30 / 30 / 30 | 1.2 ha | all three identical | NPK 115.385, Urea 40.134 | 0.0001 / 0.0001 / 0.0001 | 3,629.91 | 4,355.89 |
| 5 | Wheat tillering, P+K only (M4 #6) | 0 / 60 / 40 | 1 ha | **LOWEST_COST** | DAP 43.479, NPK 153.847 | **23.2109** / 0.0006 / 0.0002 | **5,697.03** | 5,697.03 |
| | | | | **MIN_EXCESS** | MOP 66.667, SSP 375.000 | **0.0000** / 0 / 0.0002 | **6,391.68** | 6,391.68 |
| | | | | **BALANCED** | MOP 19.754, NPK 108.262, SSP 199.076 | **10.8262** / 0.0003 / 0.0005 | **6,044.37** | 6,044.37 |
| 6 | Upper bound (urea ≤ 100) | 90 / 0 / 0 | 1 ha | all three identical | Urea 100 (at cap), DAP 244.445 | 0.0001 / **112.4447** / 0 | 7,192.01 | 7,192.01 |
| 7 | Tiny field | 33.333… / 0.1 / 0.2 | 0.001 ha | all three identical | Urea 72.381, NPK 0.385, MOP 0.167 | 0.0004 / 0.0001 / 0.0003 | 445.49 | 0.45 |
| 8 | Huge field | 120 / 60 / 40 | 250,000 ha | all three identical | DAP 43.479, NPK 153.847, Urea 210.412 | 0.0004 / 0.0006 / 0.0002 | 6,942.67 | 1,735,668,460 |
| 9 | Nothing due | 0 / 0 / 0 | 1 ha | `NOTHING_REQUIRED` | — | — | 0 | 0 |
| 10 | No potash source (urea + DAP only) | 40 / 30 / 20 | 1 ha | `INFEASIBLE` | — | K₂O: max 0, "no available fertilizer contains K2O" | — | — |

Reading the results:

- **When a zero-excess plan is also the cheapest (1–4, 7, 8), all three strategies coincide.** With N required
  alongside P and K, urea absorbs the N in DAP/NPK, so there is no conflict between cost and excess, and the response
  says so (`same_as`).
- **Example 5 is the case the three strategies exist for.** Only P and K are due, and the cheapest P sources
  (DAP, NPK) also carry N. The cheapest plan brings 23.2 kg/ha of N that nobody asked for. The zero-excess plan
  (SSP + MOP) costs ₹694.65/ha more and is 2.2× the product mass. BALANCED costs ₹347.34 more (exactly half the
  premium) and supplies 10.83 kg/ha of excess N, 53 % less than the cheapest plan (≥ 50 % guaranteed).
- **Example 6 shows that caps are not free.** With urea capped at 100 kg/ha, the remaining N has to come from DAP,
  and every plan over-supplies 112 kg/ha P₂O₅. The response carries a cap warning and an "unavoidable excess"
  warning. The cap here is a test setting; the default is 500.
- Example 3 was cross-checked against an independent solve with HiGHS interior point (`highs-ipm`); the costs agree
  to within ₹0.05 of rounding.

Excerpt of one plan in the response (example 5, `BALANCED`):

```json
{
  "strategy": "BALANCED",
  "objective": {
    "expression": "minimise sum_j (supplied_j - required_j) subject to sum_i price_i * x_i <= C_LOWEST_COST + 0.5 * (C_MIN_EXCESS - C_LOWEST_COST)",
    "tie_break": "lowest cost", "value": 10.827, "unit": "kg nutrient/ha"
  },
  "items": [
    {"code": "MOP", "kg_ha": 19.754, "field_kg": 19.754, "cost_per_ha": 671.636, "at_upper_bound": false, "...": "..."},
    {"code": "NPK_10_26_26", "kg_ha": 108.262, "...": "..."},
    {"code": "SSP", "kg_ha": 199.076, "...": "..."}
  ],
  "supplied_kg_ha": {"n": 10.8262, "p2o5": 60.0003, "k2o": 40.0005},
  "excess_kg_ha": {"n": 10.8262, "p2o5": 0.0003, "k2o": 0.0005},
  "total_excess_kg_ha": 10.827, "cost_per_ha": 6044.3748, "total_mass_kg_ha": 327.092,
  "feasible": true, "same_as": []
}
```

(Values rounded here for readability. The response carries full doubles so that Java can recompute them exactly.)

## 9. Verification in Spring Boot

`FertilizerPlanVerifier.verify(requiredKgHa, areaHa, catalogue, plan)` is pure Java (package
`com.agrioptima.engine.plan`). It takes the **backend's own** catalogue (grades, prices, caps) and the plan's product
quantities, and recomputes supply, excess, cost and mass per ha and per field. It reports a violation for:
an unknown or duplicated product; a quantity that is negative, NaN, infinite or above the cap; a field quantity that
is not kg/ha × area; supply < requirement − 10⁻⁹; any claimed total that differs from the recomputation by more than
10⁻⁹ relative; and `feasible` claimed differently from the re-check. The returned metrics are the recomputed ones,
never copies of the optimizer's.

Cross-language contract: `ml-service/tests/optimizer_contract.py` runs the real optimizer on 10 cases and writes
`backend/src/test/resources/optimizer/optimizer-contract.json`. `FertilizerPlanVerifierContractTest` re-checks all 27
returned plans in Java. It also shows that removing 1 % of every product breaks a requirement in every plan, and that
a price change after the optimizer ran is detected. A pytest fails if the file no longer equals the current optimizer
output. After an intentional change, regenerate it with `python -m tests.optimizer_contract` (from `ml-service/`).

Wiring the verifier into the recommendation flow (drop any plan that fails) is part of Milestone 7.

## 10. Tests (actual results, 2026-09-27)

- `.\scripts\ml.ps1 test` → **96 passed** (46 from M3 + 50 in `tests/test_optimizer.py`), about 6 s.
- `.\scripts\backend.ps1 build` → **Tests run: 150, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**
  (130 from M2–M4 + 16 `FertilizerPlanVerifierTest` + 4 `FertilizerPlanVerifierContractTest`).
- Mutation checks (then reverted): removing the round-up fallback and the requirement margin in Python made 19
  tests fail (the optimizer refused to return under-supplying plans). Loosening the Java requirement tolerance to
  10⁻³ made 3 verifier tests fail.
- Timing: 1,000 random requests (3 plans each, up to 5 LP solves per plan) took 12.2 s in total, about 12 ms per
  request. None of the 3,000 plans contained a quantity ≤ 2 g/ha.

## 11. Limitations

- **Linear, single-application model.** No nutrient losses, no use-efficiency differences between products, no
  interaction with timing (the split across stages is M4's job), and no secondary nutrients or micronutrients
  (S in SSP, Zn), which are not credited.
- **Excess is unweighted kg**; N, P₂O₅ and K₂O count equally (§3).
- **Prices are indicative and static.** Subsidised versus market prices, availability at the local dealer and bag
  sizes are not modelled. Quantities are not rounded to whole bags.
- **The 500 kg/ha default cap is a sanity limit, not agronomy.** A per-crop / per-product maximum single dose from a
  cited source would be better.
- **BALANCED depends on β**, a prototype choice. The trade-off between money and excess should be the farmer's call;
  M9 (what-if) can expose it.
- **Requirement quality is inherited.** A wrong requirement from M4 (e.g. a wrong profile for the zone) gives an
  optimally satisfied wrong requirement.
- **No product-mixing or compatibility rules** (e.g. application method, granule compatibility), and no rule that
  zero-excess plans should prefer fewer products.
