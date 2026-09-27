#!/usr/bin/env sh
# Runs the Maven wrapper for the backend with the portable JDK 21 from .tools/ (never the system Java).
# Usage (from repo root):  scripts/backend.sh run|test|build
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="$(ls -d "$ROOT"/.tools/jdk-21* 2>/dev/null | head -n 1)"
if [ -z "$JDK" ]; then
  echo "JDK 21 not found in .tools/. See README 'Local setup'." >&2
  exit 1
fi
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

cd "$ROOT/backend"
case "${1:-run}" in
  run)   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev ;;
  test)  ./mvnw test ;;
  build) ./mvnw clean package ;;
  *)     echo "usage: $0 run|test|build" >&2; exit 2 ;;
esac
