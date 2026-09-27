"""Request/response models for yield inference."""

from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.features import CATEGORIES, normalise_previous_crop, normalise_state, normalise_token, sowing_day


class YieldScenario(BaseModel):
    """One field + one candidate fertilizer scenario. All values are known before fertilizer is applied."""

    model_config = ConfigDict(extra="forbid")

    crop: Literal["WHEAT", "RICE"]
    state: str = Field(description="Indian state, e.g. BIHAR, UTTAR_PRADESH")
    sowing_date: date = Field(description="Sowing (wheat) or transplanting/sowing (rice) date")
    soil_texture: Literal["LIGHT", "MEDIUM", "HEAVY"] | None = None
    variety_type: Literal["IMPROVED", "HYBRID", "LOCAL"] | None = None
    previous_crop: str | None = None
    irrigation_available: bool
    fym_applied: bool = False
    zn_applied: bool = False
    n_kg_ha: float = Field(ge=0, le=1000, description="Total N applied, kg/ha")
    p2o5_kg_ha: float = Field(ge=0, le=1000, description="Total P2O5 applied, kg/ha")
    k2o_kg_ha: float = Field(ge=0, le=1000, description="Total K2O applied, kg/ha")

    @field_validator("crop", "soil_texture", "variety_type", mode="before")
    @classmethod
    def _upper(cls, v):
        return normalise_token(v) if isinstance(v, str) else v

    @field_validator("state")
    @classmethod
    def _state(cls, v: str) -> str:
        state = normalise_state(v)
        if state not in CATEGORIES["state"]:
            raise ValueError(f"state must be one of {list(CATEGORIES['state'])}")
        return state

    @field_validator("previous_crop")
    @classmethod
    def _previous_crop(cls, v: str | None) -> str | None:
        return normalise_previous_crop(v)

    def to_feature_record(self) -> dict:
        return {
            "n_kg_ha": self.n_kg_ha,
            "p2o5_kg_ha": self.p2o5_kg_ha,
            "k2o_kg_ha": self.k2o_kg_ha,
            "sowing_day": sowing_day(self.crop, self.sowing_date),
            "zn_applied": float(self.zn_applied),
            "irrigation_available": float(self.irrigation_available),
            "fym_applied": float(self.fym_applied),
            "crop": self.crop,
            "state": self.state,
            "soil_texture": self.soil_texture,
            "variety_type": self.variety_type,
            "previous_crop": self.previous_crop,
        }


class ClippedInput(BaseModel):
    """A numeric model input that was outside the range seen in training and was clipped before prediction."""

    feature: str = Field(description="n_kg_ha, p2o5_kg_ha, k2o_kg_ha (kg/ha) or sowing_day (days after 1 Oct for "
                                     "wheat / 1 May for rice, derived from sowing_date)")
    input_value: float = Field(description="Value derived from the request")
    used_value: float = Field(description="Value the model actually used (nearest supported bound)")
    supported_min: float
    supported_max: float


class YieldPrediction(BaseModel):
    predicted_yield_t_ha: float = Field(description="Predicted grain yield, t/ha (never negative)")
    extrapolation: bool = Field(description="True if any numeric input was outside the supported range and clipped")
    clipped_features: list[str] = Field(description="Names of the clipped inputs (details in clipped_inputs)")
    clipped_inputs: list[ClippedInput] = Field(default_factory=list)
    model_version: str
