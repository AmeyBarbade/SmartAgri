"""Service settings, read from environment variables once at startup."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from app.model_store import artifact_dir_from_env

SERVICE_NAME = "agrioptima-ml"
SERVICE_VERSION = "0.6.0"

# The ML service is internal (called by the Spring backend, server to server), so browsers normally never call it.
# The default only allows the local React dev server, for trying the API from the browser during development.
DEFAULT_CORS_ORIGINS = "http://localhost:5173"


class ConfigError(ValueError):
    pass


@dataclass(frozen=True)
class Settings:
    artifact_dir: Path
    cors_allowed_origins: tuple[str, ...]

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(artifact_dir=artifact_dir_from_env(),
                   cors_allowed_origins=parse_origins(os.environ.get("ML_CORS_ALLOWED_ORIGINS", DEFAULT_CORS_ORIGINS)))


def parse_origins(raw: str) -> tuple[str, ...]:
    """Comma-separated exact origins. Empty disables CORS. A wildcard is refused: list the origins explicitly."""
    origins = tuple(o.strip().rstrip("/") for o in raw.split(",") if o.strip())
    for origin in origins:
        if "*" in origin:
            raise ConfigError("ML_CORS_ALLOWED_ORIGINS must list explicit origins; '*' is not allowed")
        if not origin.startswith(("http://", "https://")):
            raise ConfigError(f"ML_CORS_ALLOWED_ORIGINS: '{origin}' is not an http(s) origin")
    return origins
