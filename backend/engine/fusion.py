"""
Fusion & Optimizer Layer

Orchestrates:  STCR → ML fallback → bag conversion → cost → weather → IPNS
"""
import math
from .stcr import calculate_stcr_dose
from .ml_layer import predict_ml_dose
from .weather import get_weather_data
from .cost import calculate_cost
from .bag_converter import convert_to_bags
import logging

logger = logging.getLogger(__name__)

# Ideal soil micronutrient ranges (general approximation)
IDEAL_MICRO = {
    "S": 10.0,
    "Zn": 0.6,
    "Fe": 4.5,
    "Cu": 0.2,
    "Mn": 2.0,
    "B": 0.5,
    "pH": (6.5, 7.5),
    "EC": (0.0, 1.0),
    "organic_carbon": (0.5, 0.75)
}


def check_micronutrients(soil_data: dict):
    shortfall = {}
    for nutrient, threshold in IDEAL_MICRO.items():
        val = soil_data.get(nutrient, 0)
        if isinstance(threshold, tuple):
            if val < threshold[0]:
                shortfall[nutrient] = f"Low (Target: {threshold[0]}-{threshold[1]})"
            elif val > threshold[1]:
                shortfall[nutrient] = f"High (Target: {threshold[0]}-{threshold[1]})"
        else:
            if val < threshold:
                shortfall[nutrient] = round(threshold - val, 2)
    return shortfall


def format_bag_qty(bags: float, kg: float = None) -> str:
    """Formats bag quantity cleanly for display."""
    if bags <= 0:
        return "0 bags"
    bag_str = f"{int(bags)} bags" if bags.is_integer() else f"{bags:.2f} bags"
    if kg is not None and kg > 0:
        return f"{bag_str} ({kg:.1f} kg)"
    return bag_str


def generate_schedule(bags: dict):
    """Build a split application schedule with precise dynamic acreage scaling."""
    schedule = []
    urea = bags["urea"]
    dap  = bags["dap"]
    mop  = bags["mop"]

    has_basal = (urea["bags"] > 0 or dap["bags"] > 0 or mop["bags"] > 0)

    # Split Urea and MOP 50% Basal / 50% Top Dressing
    urea_basal = round(urea["bags"] * 0.5, 2)
    urea_top   = round(urea["bags"] - urea_basal, 2)
    urea_basal_kg = round(urea["kg"] * 0.5, 1)
    urea_top_kg   = round(urea["kg"] - urea_basal_kg, 1)

    mop_basal = round(mop["bags"] * 0.5, 2)
    mop_top   = round(mop["bags"] - mop_basal, 2)
    mop_basal_kg = round(mop["kg"] * 0.5, 1)
    mop_top_kg   = round(mop["kg"] - mop_basal_kg, 1)

    if has_basal:
        schedule.append({
            "stage": "Basal Dose (Sowing)",
            "fertilizer": "DAP + Urea + MOP",
            "quantity": {
                "DAP": format_bag_qty(dap["bags"], dap["kg"]),
                "Urea": format_bag_qty(urea_basal, urea_basal_kg),
                "MOP": format_bag_qty(mop_basal, mop_basal_kg)
            },
            "timing_note": "Apply all DAP plus 50% Urea and 50% MOP evenly at sowing time."
        })
    if urea_top > 0 or mop_top > 0:
        schedule.append({
            "stage": "Top Dressing (30–45 days)",
            "fertilizer": "Urea + MOP",
            "quantity": {
                "Urea": format_bag_qty(urea_top, urea_top_kg),
                "MOP": format_bag_qty(mop_top, mop_top_kg)
            },
            "timing_note": "Apply remaining Urea and MOP before irrigation or light rainfall."
        })
    return schedule


def get_recommendation(
    crop_type: str,
    land_size_acres: float,
    target_yield: float,
    soil_data: dict,
    location: dict,
    previous_usage: dict = None
):
    # 1. Weather
    weather = get_weather_data(location.get("lat", 0), location.get("lon", 0))

    # 2. STCR (primary) → ML (fallback)
    confidence = "stcr_grounded"
    base_doses = calculate_stcr_dose(crop_type, target_yield, soil_data)

    if not base_doses:
        confidence = "ml_estimated"
        ml_out = predict_ml_dose(crop_type, soil_data, weather)
        if ml_out:
            # ML outputs elemental N/P/K → approximate to oxide form
            base_doses = {
                "N":    ml_out.get("N", 0),
                "P2O5": ml_out.get("P", 0) * 2.29,   # P → P2O5
                "K2O":  ml_out.get("K", 0) * 1.20,    # K → K2O
            }

    if not base_doses:
        base_doses = {"N": 0, "P2O5": 0, "K2O": 0}

    # Scale to land size (STCR gives per hectare; 1 acre ≈ 0.4047 ha)
    ha = land_size_acres * 0.4047
    scaled_doses = {k: round(v * ha, 2) for k, v in base_doses.items()}

    # 3. Commercial bag conversion with dynamic acreage scaling
    commercial_bags = convert_to_bags(scaled_doses, land_size_acres=land_size_acres)

    # 4. Cost
    cost_comp = calculate_cost(commercial_bags, previous_usage)

    # 5. Micronutrient shortfall
    micro_shortfall = check_micronutrients(soil_data)

    # 6. Schedule (now uses proportional bags with dynamic split)
    schedule = generate_schedule(commercial_bags)

    # 7. Explainability
    explainability = {
        "N":  {"actual": soil_data.get("N", 0),  "target": 280.0},
        "P":  {"actual": soil_data.get("P", 0),  "target": 22.0},
        "K":  {"actual": soil_data.get("K", 0),  "target": 140.0},
        "Zn": {"actual": soil_data.get("Zn", 0), "target": IDEAL_MICRO["Zn"]},
        "S":  {"actual": soil_data.get("S", 0),  "target": IDEAL_MICRO["S"]}
    }

    # 8. IPNS (organic blending — 25% N replacement)
    n_dose = scaled_doses.get("N", 0)
    ipns_alternative = None
    if n_dose > 0:
        chemical_n = round(n_dose * 0.75, 2)
        organic_n  = n_dose - chemical_n
        fym_qty    = round(organic_n * 200, 2)  # FYM ~0.5% N
        ipns_alternative = {
            "chemical_n_kg": chemical_n,
            "fym_vermicompost_kg": fym_qty,
            "note": f"Replace 25% of chemical Nitrogen with {fym_qty} kg of Farm Yard Manure or Vermicompost."
        }

    return {
        "nutrient_shortfall": {**scaled_doses, **micro_shortfall},
        "commercial_bags": commercial_bags,
        "application_schedule": schedule,
        "weather_flag": weather["heavy_rain_warning"],
        "weather_reason": weather["warning_reason"],
        "cost_comparison": cost_comp,
        "confidence": confidence,
        "explainability": explainability,
        "ipns_alternative": ipns_alternative
    }
