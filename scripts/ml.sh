#!/usr/bin/env sh
# ML service helper. Uses ml-service/.venv (create it once: see README "ML service").
# Usage (from repo root):  scripts/ml.sh download|prepare|train|pipeline|test|serve|optimize [request.json]
# serve: FastAPI on http://localhost:8001 (/docs, /health, /model/info, /predict-yield, /optimize)
#                          scripts/ml.sh optimize [request.json]   (path relative to ml-service/)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/ml-service"
if [ -x .venv/Scripts/python.exe ]; then PY=.venv/Scripts/python.exe; else PY=.venv/bin/python; fi
if [ ! -x "$PY" ]; then
  echo "ml-service/.venv not found. See README 'ML service'." >&2
  exit 1
fi
case "${1:-test}" in
  download) "$PY" -m training.download_data ;;
  prepare)  "$PY" -m training.prepare_data ;;
  train)    "$PY" -m training.train ;;
  pipeline) "$PY" -m training.download_data && "$PY" -m training.prepare_data && "$PY" -m training.train ;;
  test)     "$PY" -m pytest ;;
  serve)    "$PY" -m uvicorn app.main:app --port 8001 ;;
  optimize) "$PY" -m app.optimizer "${2:-examples/optimizer/m4_6_wheat_tillering_pk_only_1ha.json}" ;;
  *)        echo "usage: $0 download|prepare|train|pipeline|test|serve|optimize [request.json]" >&2; exit 2 ;;
esac
