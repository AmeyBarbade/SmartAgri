"""
FastAPI Server for SmartAgri
Author: Amey Barbade
"""

from fastapi import FastAPI, Query
from pydantic import BaseModel, Field
from typing import Optional, Dict
from engine.fusion import get_recommendation
from engine.gis import fetch_isric_soilgrids, calculate_polygon_metrics

app = FastAPI(title="SmartAgri — Sustainable Fertilizer Usage Optimizer API")

class SoilData(BaseModel):
    N: float = 0
    P: float = 0
    K: float = 0
    S: float = 0
    Zn: float = 0
    Fe: float = 0
    Cu: float = 0
    Mn: float = 0
    B: float = 0
    pH: float = 7.0
    EC: float = 0.5
    organic_carbon: float = 0.6

class Location(BaseModel):
    lat: float
    lon: float

class RecommendationRequest(BaseModel):
    crop_type: str
    land_size_acres: float
    target_yield: float
    previous_fertilizer_usage: Optional[Dict[str, float]] = None
    location: Location
    soil_data: SoilData

@app.post("/recommend")
def recommend_fertilizer(req: RecommendationRequest):
    res = get_recommendation(
        crop_type=req.crop_type,
        land_size_acres=req.land_size_acres,
        target_yield=req.target_yield,
        soil_data=req.soil_data.dict(),
        location=req.location.dict(),
        previous_usage=req.previous_fertilizer_usage
    )
    return res

@app.get("/soilgrids")
def query_soilgrids(lat: float = Query(..., description="Latitude"), lon: float = Query(..., description="Longitude")):
    """
    Fetch satellite-derived soil properties (N, pH, Organic Carbon) from ISRIC SoilGrids
    with failsafe fallback to Regional Vertisol simulation.
    """
    return fetch_isric_soilgrids(lat, lon)
