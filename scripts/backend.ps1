# Runs the Maven wrapper for the backend with the portable JDK 21 from .tools/ (never the system Java).
# Usage (from repo root):
#   .\scripts\backend.ps1 run     # start API with the dev profile (H2) on http://localhost:8080
#   .\scripts\backend.ps1 test    # run the full test suite
#   .\scripts\backend.ps1 build   # compile, test and package the jar
param([ValidateSet('run', 'test', 'build')][string]$Task = 'run')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jdk = Get-ChildItem -Directory (Join-Path $root '.tools') -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdk) { throw "JDK 21 not found in .tools/. See README 'Local setup'." }

$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($jdk.FullName)\bin;$env:Path"

Push-Location (Join-Path $root 'backend')
try {
    switch ($Task) {
        'run'   { .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" }
        'test'  { .\mvnw.cmd test }
        'build' { .\mvnw.cmd clean package }
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
