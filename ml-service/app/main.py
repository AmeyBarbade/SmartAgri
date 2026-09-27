"""AgriOptima ML service (FastAPI).

Routes (app/api/routes.py) -> schemas (app/api/schemas.py) -> use cases (app/services.py) -> model_store / optimizer.
Run: uvicorn app.main:app --port 8001   (docs at /docs, OpenAPI at /openapi.json)
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import router
from app.config import SERVICE_VERSION, Settings
from app.errors import install_error_handlers
from app.services import ModelState

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")  # no-op if configured
log = logging.getLogger("agrioptima.ml")

DESCRIPTION = """
Internal service called by the AgriOptima backend: yield prediction (trained model) and fertilizer
optimization (linear program).

**Units:** yield t/ha · nutrient requirement/supply/excess kg/ha (N, P2O5, K2O) · fertilizer quantities
kg product/ha · `*_field_kg` whole-field kg · costs INR/ha and INR for the whole field.

**Errors:** RFC 7807 `application/problem+json` (`type` = `urn:agrioptima:problem:<kind>`): 400 malformed JSON,
422 validation / unsupported crop, 503 model not loaded, 500 internal.
"""


def create_app(settings: Settings | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.model_state = ModelState.load(app.state.settings.artifact_dir)
        yield

    app = FastAPI(title="AgriOptima ML service", version=SERVICE_VERSION, description=DESCRIPTION,
                  lifespan=lifespan)
    app.state.settings = settings or Settings.from_env()
    if app.state.settings.cors_allowed_origins:
        app.add_middleware(CORSMiddleware, allow_origins=list(app.state.settings.cors_allowed_origins),
                           allow_methods=["GET", "POST"], allow_headers=["Content-Type"], allow_credentials=False,
                           max_age=600)
    install_error_handlers(app)
    app.include_router(router)
    return app


app = create_app()
