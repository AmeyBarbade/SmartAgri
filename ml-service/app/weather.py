"""Weather service integration using the Open-Meteo API.

Fetches 7-day weather forecast to detect heavy precipitation (> 20mm in 3 days)
that poses fertilizer runoff and nutrient leaching hazards.
"""

from __future__ import annotations

import logging
from typing import Any
import httpx

logger = logging.getLogger(__name__)


def get_weather_data(lat: float, lon: float) -> dict[str, Any]:
    """Queries Open-Meteo forecast API for temperature and precipitation risks.

    Returns safe defaults if network or external API errors occur.
    """
    url = (
        f"https://api.open-meteo.com/v1/forecast?"
        f"latitude={lat}&longitude={lon}&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto"
    )
    try:
        with httpx.Client(timeout=5.0) as client:
            response = client.get(url)
            response.raise_for_status()
            data = response.json()

        daily = data.get("daily", {})
        precip = daily.get("precipitation_sum", [])
        temp_max = daily.get("temperature_2m_max", [])

        # Heavy rainfall flag if > 20 mm forecast in the next 3 days
        heavy_rain = any(p is not None and p > 20.0 for p in precip[:3]) if precip else False

        avg_temp = sum(t for t in temp_max[:3] if t is not None) / max(len(temp_max[:3]), 1) if temp_max else 25.0
        total_rain = sum(p for p in precip[:7] if p is not None) if precip else 0.0

        return {
            "temperature": round(avg_temp, 1),
            "rainfall_7d_mm": round(total_rain, 1),
            "heavy_rain_warning": heavy_rain,
            "warning_reason": (
                "Heavy rainfall forecast in the next 3 days (>20mm). High risk of fertilizer runoff and nutrient leaching."
                if heavy_rain
                else None
            ),
        }
    except Exception as e:
        logger.warning("Failed to fetch weather data for (%s, %s): %s. Using default baseline.", lat, lon, e)
        return {
            "temperature": 25.0,
            "rainfall_7d_mm": 50.0,
            "heavy_rain_warning": False,
            "warning_reason": None,
        }
