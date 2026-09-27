import yaml
from pathlib import Path
import logging

logger = logging.getLogger(__name__)

CONFIG_PATH = Path(__file__).parent.parent / "config" / "prices.yaml"

def load_prices():
    with open(CONFIG_PATH, "r") as f:
        return yaml.safe_load(f)

def calculate_cost(doses: dict, current_usage: dict = None):
    prices = load_prices().get("fertilizers", {})
    
    # We map NPK doses roughly to Urea, DAP, MOP for cost estimation
    # 1 kg N ~ 2.17 kg Urea (46% N)
    # 1 kg P ~ 2.17 kg DAP (46% P)
    # 1 kg K ~ 1.66 kg MOP (60% K)
    
    recommended_cost = 0
    urea_needed = (doses.get("N", 0) / 0.46)
    dap_needed = (doses.get("P", 0) / 0.46)
    mop_needed = (doses.get("K", 0) / 0.60)
    
    recommended_cost += urea_needed * prices.get("Urea", 0)
    recommended_cost += dap_needed * prices.get("DAP", 0)
    recommended_cost += mop_needed * prices.get("MOP", 0)
    
    current_cost = 0
    if current_usage:
        current_cost += current_usage.get("Urea", 0) * prices.get("Urea", 0)
        current_cost += current_usage.get("DAP", 0) * prices.get("DAP", 0)
        current_cost += current_usage.get("MOP", 0) * prices.get("MOP", 0)
        
    return {
        "recommended_cost": round(recommended_cost, 2),
        "current_usage_cost": round(current_cost, 2) if current_usage else 0.0,
        "savings": round(current_cost - recommended_cost, 2) if current_usage else 0.0
    }
