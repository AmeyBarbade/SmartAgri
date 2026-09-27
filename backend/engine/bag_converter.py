"""
Commercial Fertilizer Bag Converter
Author: Amey Barbade

Converts raw elemental/oxide nutrient requirements (N, P2O5, K2O in kg)
into physical commercial fertilizer bags that a farmer can actually buy.

Calculation order (critical):
  1. DAP first  → fulfils P2O5 requirement.  DAP also supplies 18% N.
  2. Urea next  → fulfils remaining N after subtracting N supplied by DAP.
  3. MOP last   → fulfils K2O requirement.
"""
import math
import yaml
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

PRICES_PATH = Path(__file__).parent.parent / "config" / "prices.yaml"

def _load_prices():
    with open(PRICES_PATH, "r") as f:
        return yaml.safe_load(f)

def convert_to_bags(doses: dict) -> dict:
    """
    Parameters
    ----------
    doses : dict
        Keys: N, P2O5, K2O  (kg required for the field)

    Returns
    -------
    dict with keys:
        dap, urea, mop  — each a dict of {bags, kg, nutrient_supplied, cost}
        total_cost       — sum of all bag costs
    """
    cfg = _load_prices()["bags"]

    n_required    = doses.get("N", 0)
    p2o5_required = doses.get("P2O5", 0)
    k2o_required  = doses.get("K2O", 0)

    # ── 1. DAP (fulfil P2O5 first) ──────────────────────────────────────
    dap_cfg      = cfg["DAP"]
    dap_p2o5_frac = dap_cfg["composition"]["P2O5"]  # 0.46
    dap_n_frac    = dap_cfg["composition"]["N"]      # 0.18
    dap_bag_kg    = dap_cfg["bag_weight_kg"]          # 50

    dap_kg_needed = p2o5_required / dap_p2o5_frac if dap_p2o5_frac else 0
    dap_bags      = math.ceil(dap_kg_needed / dap_bag_kg) if dap_kg_needed > 0 else 0
    dap_kg_actual = dap_bags * dap_bag_kg

    n_from_dap    = dap_kg_actual * dap_n_frac   # bonus N from DAP
    n_remaining   = max(0.0, n_required - n_from_dap)

    # ── 2. Urea (fulfil remaining N) ───────────────────────────────────
    urea_cfg     = cfg["Urea"]
    urea_n_frac  = urea_cfg["composition"]["N"]      # 0.46
    urea_bag_kg  = urea_cfg["bag_weight_kg"]          # 45

    urea_kg_needed = n_remaining / urea_n_frac if urea_n_frac else 0
    urea_bags      = math.ceil(urea_kg_needed / urea_bag_kg) if urea_kg_needed > 0 else 0
    urea_kg_actual = urea_bags * urea_bag_kg

    # ── 3. MOP (fulfil K2O) ─────────────────────────────────────────────
    mop_cfg     = cfg["MOP"]
    mop_k2o_frac = mop_cfg["composition"]["K2O"]     # 0.60
    mop_bag_kg   = mop_cfg["bag_weight_kg"]           # 50

    mop_kg_needed = k2o_required / mop_k2o_frac if mop_k2o_frac else 0
    mop_bags      = math.ceil(mop_kg_needed / mop_bag_kg) if mop_kg_needed > 0 else 0
    mop_kg_actual = mop_bags * mop_bag_kg

    # ── Costs ───────────────────────────────────────────────────────────
    urea_cost = urea_bags * urea_cfg["price_per_bag"]
    dap_cost  = dap_bags  * dap_cfg["price_per_bag"]
    mop_cost  = mop_bags  * mop_cfg["price_per_bag"]

    return {
        "urea": {
            "bags": urea_bags,
            "kg": round(urea_kg_actual, 2),
            "n_supplied_kg": round(urea_kg_actual * urea_n_frac, 2),
            "cost": urea_cost,
            "bag_spec": f"{urea_bag_kg} kg bag @ ₹{urea_cfg['price_per_bag']}"
        },
        "dap": {
            "bags": dap_bags,
            "kg": round(dap_kg_actual, 2),
            "n_supplied_kg": round(n_from_dap, 2),
            "p2o5_supplied_kg": round(dap_kg_actual * dap_p2o5_frac, 2),
            "cost": dap_cost,
            "bag_spec": f"{dap_bag_kg} kg bag @ ₹{dap_cfg['price_per_bag']}"
        },
        "mop": {
            "bags": mop_bags,
            "kg": round(mop_kg_actual, 2),
            "k2o_supplied_kg": round(mop_kg_actual * mop_k2o_frac, 2),
            "cost": mop_cost,
            "bag_spec": f"{mop_bag_kg} kg bag @ ₹{mop_cfg['price_per_bag']}"
        },
        "total_cost": urea_cost + dap_cost + mop_cost
    }
