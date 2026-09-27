import yaml
import logging
from pathlib import Path

# ── STCR Rule Engine ──
# Implements ICAR Soil Test Crop Response (Targeted Yield) equations.
# Equation form:  F = (a * T) - (b * S)
#   T = target yield (q/ha)
#   S = soil test value (kg/ha)
# Output keys: N, P2O5, K2O  (oxide form, matching commercial fertilizer specs)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

CONFIG_PATH = Path(__file__).parent.parent / "config" / "stcr_coefficients.yaml"

def load_stcr_config():
    with open(CONFIG_PATH, "r") as f:
        return yaml.safe_load(f)

STCR_CONFIG = load_stcr_config()

def calculate_stcr_dose(crop_type: str, target_yield: float, soil_data: dict):
    """
    Calculate fertilizer dose using STCR equation.

    Returns dict with keys {N, P2O5, K2O} in kg, or None if no
    coefficients are available (triggers ML fallback).
    """
    crop_type = crop_type.lower()
    crop_config = STCR_CONFIG.get("crops", {}).get(crop_type)

    if not crop_config:
        logger.warning(f"STCR rules not found for crop: {crop_type}. Yielding to ML fallback.")
        return None

    if crop_config.get("status") == "coefficients pending":
        logger.warning(
            f"LOUD WARNING: {crop_type} has no verified coefficients loaded. "
            "Yielding to ML fallback."
        )
        return None

    status = crop_config.get("status", "unknown")
    source = crop_config.get("source", "unknown")
    if status == "stubbed":
        logger.info(f"Using STUBBED coefficients for {crop_type} (not verified)")
    else:
        logger.info(f"Using VERIFIED ICAR coefficients for {crop_type} [{source}]")

    # Map nutrient keys to the soil_data keys they draw from
    nutrient_soil_map = {
        "N":    "N",
        "P2O5": "P",   # soil test reports elemental P (kg/ha)
        "K2O":  "K",   # soil test reports elemental K (kg/ha)
    }

    doses = {}
    for nutrient_key in ["N", "P2O5", "K2O"]:
        coeff = crop_config.get(nutrient_key)
        if coeff:
            a = coeff["a"]
            b = coeff["b"]
            soil_key = nutrient_soil_map[nutrient_key]
            soil_test_value = soil_data.get(soil_key, 0)

            dose = (a * target_yield) - (b * soil_test_value)
            doses[nutrient_key] = max(0.0, round(dose, 2))

    return doses
