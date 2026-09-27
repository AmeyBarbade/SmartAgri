import requests
import logging

logger = logging.getLogger(__name__)

def get_weather_data(lat: float, lon: float):
    """
    Call Open-Meteo API for weather forecast.
    Check for heavy rainfall to flag leaching/runoff risks.
    Returns dict with basic stats and flag.
    """
    # Using open-meteo free API for 7 day forecast
    url = f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto"
    
    try:
        response = requests.get(url, timeout=5)
        response.raise_for_status()
        data = response.json()
        
        daily = data.get("daily", {})
        precip = daily.get("precipitation_sum", [])
        
        # Heavy rain > 20mm in next 3 days
        heavy_rain = any(p > 20 for p in precip[:3] if p is not None)
        
        # Simple averages for ML model
        avg_temp = 25.0
        if "temperature_2m_max" in daily:
            temps = [t for t in daily["temperature_2m_max"][:3] if t is not None]
            if temps:
                avg_temp = sum(temps) / len(temps)
                
        total_rain = sum([p for p in precip[:7] if p is not None])
        
        return {
            "temperature": avg_temp,
            "rainfall": total_rain, # mm
            "heavy_rain_warning": heavy_rain,
            "warning_reason": "Heavy rainfall forecast in the next 3 days. High risk of fertilizer runoff/leaching." if heavy_rain else None
        }
    except Exception as e:
        logger.error(f"Weather API failed: {e}")
        # Default fallback
        return {
            "temperature": 25.0,
            "rainfall": 50.0,
            "heavy_rain_warning": False,
            "warning_reason": None
        }
