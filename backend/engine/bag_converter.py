"""
Commercial Fertilizer Bag Converter
Author: Amey Barbade

Converts raw elemental/oxide nutrient requirements (N, P2O5, K2O in kg)
into physical commercial fertilizer bags that a farmer can actually buy.

Features:
- Proportional dynamic scaling for smallholder acreages (e.g. 0.48 acres costs ~half of 1 acre)
- Exact nutrient accounting (DAP first for P2O5 + bonus N, then Urea for remaining N, then MOP for K2O)
- Provides both exact proportional bags and whole sealed bag retail packaging counts
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

def convert_to_bags(doses: dict, land_size_acres: float = 1.0) -> dict:
    """
    Parameters
    ----------
    doses : dict
        Keys: N, P2O5, K2O  (kg required for the field)
    land_size_acres : float
        Acreage of the farm (used for proportional scaling)

    Returns
    -------
    dict with keys:
        dap, urea, mop  — each a dict of {bags, kg, cost, bag_spec, whole_bags_retail}
        total_cost       — sum of all bag costs
    """
    cfg = _load_prices()["bags"]

    n_required    = max(0.0, doses.get("N", 0))
    p2o5_required = max(0.0, doses.get("P2O5", 0))
    k2o_required  = max(0.0, doses.get("K2O", 0))

    # ── 1. DAP (fulfil P2O5 first) ──────────────────────────────────────
    dap_cfg       = cfg["DAP"]
    dap_p2o5_frac = dap_cfg["composition"]["P2O5"]  # 0.46
    dap_n_frac    = dap_cfg["composition"]["N"]      # 0.18
    dap_bag_kg    = dap_cfg["bag_weight_kg"]          # 50

    dap_kg_needed  = p2o5_required / dap_p2o5_frac if dap_p2o5_frac else 0.0
    dap_bags_exact = dap_kg_needed / dap_bag_kg if dap_bag_kg else 0.0
    dap_bags       = round(dap_bags_exact, 2)
    dap_cost       = round(dap_bags_exact * dap_cfg["price_per_bag"], 2)
    n_from_dap     = dap_kg_needed * dap_n_frac
    n_remaining    = max(0.0, n_required - n_from_dap)

    # ── 2. Urea (fulfil remaining N) ───────────────────────────────────
    urea_cfg     = cfg["Urea"]
    urea_n_frac  = urea_cfg["composition"]["N"]      # 0.46
    urea_bag_kg  = urea_cfg["bag_weight_kg"]          # 45

    urea_kg_needed  = n_remaining / urea_n_frac if urea_n_frac else 0.0
    urea_bags_exact = urea_kg_needed / urea_bag_kg if urea_bag_kg else 0.0
    urea_bags       = round(urea_bags_exact, 2)
    urea_cost       = round(urea_bags_exact * urea_cfg["price_per_bag"], 2)

    # ── 3. MOP (fulfil K2O) ─────────────────────────────────────────────
    mop_cfg      = cfg["MOP"]
    mop_k2o_frac = mop_cfg["composition"]["K2O"]     # 0.60
    mop_bag_kg   = mop_cfg["bag_weight_kg"]           # 50

    mop_kg_needed  = k2o_required / mop_k2o_frac if mop_k2o_frac else 0.0
    mop_bags_exact = mop_kg_needed / mop_bag_kg if mop_bag_kg else 0.0
    mop_bags       = round(mop_bags_exact, 2)
    mop_cost       = round(mop_bags_exact * mop_cfg["price_per_bag"], 2)

    # Total cost scales linearly with exact acreage
    total_cost = round(urea_cost + dap_cost + mop_cost, 2)

    return {
        "urea": {
            "bags": urea_bags,
            "whole_bags": math.ceil(urea_bags_exact) if urea_bags_exact > 0 else 0,
            "kg": round(urea_kg_needed, 1),
            "n_supplied_kg": round(urea_kg_needed * urea_n_frac, 1),
            "cost": urea_cost,
            "bag_spec": f"{urea_bag_kg} kg bag @ ₹{urea_cfg['price_per_bag']}"
        },
        "dap": {
            "bags": dap_bags,
            "whole_bags": math.ceil(dap_bags_exact) if dap_bags_exact > 0 else 0,
            "kg": round(dap_kg_needed, 1),
            "n_supplied_kg": round(n_from_dap, 1),
            "p2o5_supplied_kg": round(p2o5_required, 1),
            "cost": dap_cost,
            "bag_spec": f"{dap_bag_kg} kg bag @ ₹{dap_cfg['price_per_bag']}"
        },
        "mop": {
            "bags": mop_bags,
            "whole_bags": math.ceil(mop_bags_exact) if mop_bags_exact > 0 else 0,
            "kg": round(mop_kg_needed, 1),
            "k2o_supplied_kg": round(k2o_required, 1),
            "cost": mop_cost,
            "bag_spec": f"{mop_bag_kg} kg bag @ ₹{mop_cfg['price_per_bag']}"
        },
        "total_cost": total_cost
    }
