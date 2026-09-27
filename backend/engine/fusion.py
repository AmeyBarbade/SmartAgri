from .stcr import calculate_stcr_dose
from .ml_layer import predict_ml_dose
from .weather import get_weather_data
from .cost import calculate_cost
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

def generate_schedule(doses: dict):
    schedule = []
    # Simplified splitting logic
    n_dose = doses.get("N", 0)
    p_dose = doses.get("P", 0)
    k_dose = doses.get("K", 0)
    
    if n_dose > 0 or p_dose > 0 or k_dose > 0:
        schedule.append({
            "stage": "Basal Dose (Sowing)",
            "fertilizer": "Urea/DAP/MOP mix",
            "quantity_kg": {"N": round(n_dose * 0.5, 2), "P": round(p_dose, 2), "K": round(k_dose * 0.5, 2)},
            "timing_note": "Apply evenly at sowing time."
        })
    if n_dose > 0 or k_dose > 0:
        schedule.append({
            "stage": "Top Dressing (30 days)",
            "fertilizer": "Urea/MOP mix",
            "quantity_kg": {"N": round(n_dose * 0.5, 2), "P": 0.0, "K": round(k_dose * 0.5, 2)},
            "timing_note": "Apply before irrigation."
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
    # 1. Weather Data
    weather = get_weather_data(location.get("lat", 0), location.get("lon", 0))
    
    # 2. Try STCR first
    confidence = "stcr_grounded"
    base_doses = calculate_stcr_dose(crop_type, target_yield, soil_data)
    
    if not base_doses:
        # Fallback to ML
        confidence = "ml_estimated"
        base_doses = predict_ml_dose(crop_type, soil_data, weather)
        
    if not base_doses:
        # Failsafe
        base_doses = {"N": 0, "P": 0, "K": 0}
        
    # Scale to land size
    scaled_doses = {k: v * land_size_acres for k, v in base_doses.items()}
    
    # 3. Micronutrients Shortfall
    micro_shortfall = check_micronutrients(soil_data)
    
    # 4. Schedule
    schedule = generate_schedule(scaled_doses)
    
    # 5. Cost
    cost_comp = calculate_cost(scaled_doses, previous_usage)
    
    return {
        "nutrient_shortfall": {**scaled_doses, **micro_shortfall},
        "application_schedule": schedule,
        "weather_flag": weather["heavy_rain_warning"],
        "weather_reason": weather["warning_reason"],
        "cost_comparison": cost_comp,
        "confidence": confidence
    }
