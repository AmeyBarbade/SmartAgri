"""
GIS & Satellite SoilGrids Integration
Author: Amey Barbade

Handles:
1. Polygon geometry parsing, EPSG:6933 equal-area projection, and acreage calculation.
2. Centroid extraction for weather & soil targeting.
3. ISRIC SoilGrids REST API queries (0-5cm depth for N, pH, Organic Carbon).
4. Failsafe demo fallback for Vertisols (black soils) under strict 4-second timeout.
"""

import logging
import requests
import pyproj
from shapely.geometry import shape
from shapely.ops import transform

logger = logging.getLogger(__name__)

# Transformer from WGS84 (Lat/Lon) to equal-area projection EPSG:6933
_TRANSFORMER_4326_TO_6933 = pyproj.Transformer.from_crs("EPSG:4326", "EPSG:6933", always_xy=True)


def calculate_polygon_metrics(drawing: dict) -> dict | None:
    """
    Parses a Folium Draw GeoJSON geometry, projects it to EPSG:6933,
    and returns acreage and centroid (lat, lon).
    """
    if not drawing or not isinstance(drawing, dict):
        return None

    # Handle both Feature and raw Geometry objects
    geom_dict = drawing.get("geometry", drawing)
    if not isinstance(geom_dict, dict) or geom_dict.get("type") != "Polygon":
        return None

    try:
        geom = shape(geom_dict)
        if not geom.is_valid or geom.is_empty:
            geom = geom.buffer(0)  # attempt self-repair
            if not geom.is_valid or geom.is_empty:
                return None

        # Project to EPSG:6933 (equal-area cylindrical)
        projected = transform(_TRANSFORMER_4326_TO_6933.transform, geom)
        area_sq_m = projected.area
        acres = area_sq_m * 0.000247105

        centroid = geom.centroid
        centroid_lon = round(float(centroid.x), 4)
        centroid_lat = round(float(centroid.y), 4)

        return {
            "acres": max(0.01, round(acres, 2)),
            "area_sq_m": round(area_sq_m, 1),
            "lat": centroid_lat,
            "lon": centroid_lon,
            "geojson": geom_dict
        }
    except Exception as e:
        logger.error(f"Error calculating polygon metrics: {e}")
        return None


def fetch_isric_soilgrids(lat: float, lon: float) -> dict:
    """
    Calls ISRIC SoilGrids REST API with strict 4-second timeout.
    Returns N, pH, and organic carbon for the 0-5cm layer.
    Falls back to regional Vertisol simulation if the API times out or fails.
    """
    url = (
        f"https://rest.isric.org/soilgrids/v2.0/properties/query"
        f"?lon={lon}&lat={lat}&property=soc&property=nitrogen&property=phh2o&depth=0-5cm"
    )

    try:
        response = requests.get(url, timeout=4)
        response.raise_for_status()
        data = response.json()

        layers = {}
        for layer in data.get("properties", {}).get("layers", []):
            name = layer.get("name")
            depths = layer.get("depths", [])
            if depths and "values" in depths[0]:
                layers[name] = depths[0]["values"].get("mean", 0)

        # Parse SoilGrids properties:
        # phh2o: mapped units pH*10 (d_factor 10)
        raw_ph = layers.get("phh2o", 75)
        ph = round(raw_ph / 10.0, 1)

        # soc: mapped units dg/kg (d_factor 10) -> % is dg/kg / 100
        raw_soc = layers.get("soc", 60)
        oc = round(raw_soc / 100.0, 2)
        # clamp to realistic agricultural range if raw global raster has extreme value
        oc = max(0.20, min(1.80, oc))

        # nitrogen: mapped units cg/kg -> available SHC N estimate in kg/ha
        raw_n = layers.get("nitrogen", 130)
        n = round(raw_n * 1.35, 1)
        n = max(80.0, min(350.0, n))

        return {
            "pH": ph,
            "organic_carbon": oc,
            "N": n,
            "is_fallback": False,
            "source": "ISRIC SoilGrids v2.0 (Live Satellite Data)"
        }

    except Exception as err:
        logger.warning(f"ISRIC API failed/timed out ({err}). Using Regional Vertisol Simulation fallback.")
        # Deterministic simulation based on centroid coordinate seed
        seed = int(abs(lat * 100 + lon * 100))
        ph_sim = round(7.4 + (seed % 8) * 0.1, 1)
        oc_sim = round(0.48 + (seed % 22) * 0.01, 2)
        n_sim = round(165.0 + (seed % 45) * 1.0, 1)

        return {
            "pH": ph_sim,
            "organic_carbon": oc_sim,
            "N": n_sim,
            "is_fallback": True,
            "source": f"Regional Vertisol Simulation (Failsafe Fallback: {type(err).__name__})"
        }
