"""
Cost Economics Module

Calculates bag-based cost for recommended fertilizer plan vs. farmer's
current usage, using 2026 GoI subsidized MRP per bag.
"""
import yaml
from pathlib import Path
import logging

logger = logging.getLogger(__name__)

CONFIG_PATH = Path(__file__).parent.parent / "config" / "prices.yaml"

def load_prices():
    with open(CONFIG_PATH, "r") as f:
        return yaml.safe_load(f)

def calculate_cost(commercial_bags: dict, current_usage: dict = None):
    """
    Parameters
    ----------
    commercial_bags : dict
        Output of bag_converter.convert_to_bags() — contains per-product
        bag counts and costs plus total_cost.
    current_usage : dict | None
        Farmer's previous usage in bags: {Urea: n, DAP: n, MOP: n}
    """
    cfg = load_prices()["bags"]

    recommended_cost = commercial_bags["total_cost"]

    current_cost = 0.0
    if current_usage:
        current_cost += current_usage.get("Urea", 0) * cfg["Urea"]["price_per_bag"]
        current_cost += current_usage.get("DAP", 0)  * cfg["DAP"]["price_per_bag"]
        current_cost += current_usage.get("MOP", 0)  * cfg["MOP"]["price_per_bag"]

    return {
        "recommended_cost": round(recommended_cost, 2),
        "current_usage_cost": round(current_cost, 2) if current_usage else 0.0,
        "savings": round(current_cost - recommended_cost, 2) if current_usage else 0.0
    }
