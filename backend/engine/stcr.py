import yaml
import logging
from pathlib import Path

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

CONFIG_PATH = Path(__file__).parent.parent / "config" / "stcr_coefficients.yaml"

def load_stcr_config():
    with open(CONFIG_PATH, "r") as f:
        return yaml.safe_load(f)

STCR_CONFIG = load_stcr_config()

def calculate_stcr_dose(crop_type: str, target_yield: float, soil_data: dict):
    """
    Calculate fertilizer dose using STCR equation:
    dose = (a * target_yield) - (b * soil_test_value)
    """
    crop_type = crop_type.lower()
    crop_config = STCR_CONFIG.get("crops", {}).get(crop_type)

    if not crop_config:
        logger.warning(f"STCR rules not found for crop: {crop_type}. Yielding to ML fallback.")
        return None
    
    if crop_config.get("status") == "coefficients pending":
        # TODO: Phase 2 will provide real ICAR coefficients. DO NOT INVENT plausible numbers.
        logger.warning(f"LOUD WARNING: {crop_type} has no verified coefficients loaded. Yielding to ML fallback.")
        return None

    logger.info(f"Using STCR stubbed coefficients for {crop_type}")
    
    doses = {}
    for nutrient in ["N", "P", "K"]:
        if nutrient in crop_config:
            a = crop_config[nutrient]["a"]
            b = crop_config[nutrient]["b"]
            soil_test_value = soil_data.get(nutrient, 0)
            
            # STCR equation
            dose = (a * target_yield) - (b * soil_test_value)
            doses[nutrient] = max(0.0, round(dose, 2))  # no negative dose
    
    return doses
