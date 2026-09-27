"""API errors as RFC 7807 ``application/problem+json``, in the same shape as the Spring backend.

Clients never see stack traces, exception reprs or filesystem paths: unexpected errors are logged here and answered
with a fixed message.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

log = logging.getLogger("agrioptima.ml.errors")

TYPE_PREFIX = "urn:agrioptima:problem:"
PROBLEM_JSON = "application/problem+json"


class Problem(BaseModel):
    """RFC 7807 problem details."""

    type: str = Field(examples=["urn:agrioptima:problem:validation"])
    title: str
    status: int
    detail: str
    instance: str | None = Field(default=None, description="Request path")
    errors: dict[str, str] | None = Field(default=None, description="Field path -> message (validation errors only)")


class ApiError(Exception):
    status = 500
    type = "internal"
    title = "Internal server error"

    def __init__(self, detail: str, errors: dict[str, str] | None = None):
        super().__init__(detail)
        self.detail = detail
        self.errors = errors


class UnsupportedCropError(ApiError):
    status = 422
    type = "unsupported-crop"
    title = "Unsupported crop"


class ModelUnavailableError(ApiError):
    status = 503
    type = "model-unavailable"
    title = "Yield model unavailable"


class OptimizationFailedError(ApiError):
    status = 500
    type = "optimization-failed"
    title = "Optimization failed"


def problem_response(request: Request, status: int, type_: str, title: str, detail: str,
                     errors: dict[str, str] | None = None) -> JSONResponse:
    body = Problem(type=TYPE_PREFIX + type_, title=title, status=status, detail=detail,
                   instance=request.url.path, errors=errors)
    return JSONResponse(body.model_dump(exclude_none=True), status_code=status, media_type=PROBLEM_JSON)


def _field_path(loc: tuple) -> str:
    parts = [str(p) for p in loc if p != "body"]
    path = ""
    for p in parts:
        path += f"[{p}]" if p.isdigit() else (f".{p}" if path else p)
    return path or "body"


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def _api_error(request: Request, exc: ApiError):
        return problem_response(request, exc.status, exc.type, exc.title, exc.detail, exc.errors)

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError):
        details = exc.errors()
        if any(e.get("type") == "json_invalid" for e in details):
            return problem_response(request, 400, "malformed-request", "Malformed request",
                                    "The request body is not valid JSON.")
        errors: dict[str, str] = {}
        for e in details:  # only location + message: the offending input is never echoed back
            errors.setdefault(_field_path(tuple(e.get("loc", ()))), str(e.get("msg", "invalid value")))
        return problem_response(request, 422, "validation", "Validation failed",
                                "One or more fields are invalid.", errors)

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException):
        titles = {404: ("not-found", "Not found"), 405: ("method-not-allowed", "Method not allowed")}
        type_, title = titles.get(exc.status_code, ("http-error", "Request failed"))
        detail = exc.detail if isinstance(exc.detail, str) else title
        response = problem_response(request, exc.status_code, type_, title, detail)
        if exc.headers:
            response.headers.update(exc.headers)
        return response

    @app.exception_handler(Exception)
    async def _unexpected(request: Request, exc: Exception):
        log.error("unhandled error on %s %s", request.method, request.url.path, exc_info=exc)
        return problem_response(request, 500, "internal", "Internal server error", "An unexpected error occurred.")


ERROR_RESPONSES = {
    400: {"model": Problem, "description": "Malformed request (body is not valid JSON)",
          "content": {PROBLEM_JSON: {}}},
    422: {"model": Problem, "description": "Validation failed (`errors` maps each field path to a message)",
          "content": {PROBLEM_JSON: {}}},
    500: {"model": Problem, "description": "Internal error (no internals are exposed)", "content": {PROBLEM_JSON: {}}},
}
