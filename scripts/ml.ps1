# ML service helper. Uses ml-service/.venv (create it once: see README "ML service").
# Usage (from repo root):
#   .\scripts\ml.ps1 download   # fetch the raw LDS survey files (checksum-verified) into data/raw/lds/
#   .\scripts\ml.ps1 prepare    # validate + clean + derive features -> data/processed/
#   .\scripts\ml.ps1 train      # compare 4 models, save artifacts/ + reports/
#   .\scripts\ml.ps1 pipeline   # download + prepare + train
#   .\scripts\ml.ps1 test       # pytest
#   .\scripts\ml.ps1 serve      # FastAPI on http://localhost:8001 (/docs, /health, /model/info, /predict-yield, /optimize)
#   .\scripts\ml.ps1 optimize [request.json]   # fertilizer optimizer on a request (path relative to ml-service/)
param(
    [ValidateSet('download', 'prepare', 'train', 'pipeline', 'test', 'serve', 'optimize')][string]$Task = 'test',
    [string]$Request = 'examples\optimizer\m4_6_wheat_tillering_pk_only_1ha.json'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$py = Join-Path $root 'ml-service\.venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw "ml-service/.venv not found. See README 'ML service'." }

Push-Location (Join-Path $root 'ml-service')
try {
    $steps = @(switch ($Task) {
        'download' { ,@('-m', 'training.download_data') }
        'prepare'  { ,@('-m', 'training.prepare_data') }
        'train'    { ,@('-m', 'training.train') }
        'pipeline' { @('-m', 'training.download_data'), @('-m', 'training.prepare_data'), @('-m', 'training.train') }
        'test'     { ,@('-m', 'pytest') }
        'serve'    { ,@('-m', 'uvicorn', 'app.main:app', '--port', '8001') }
        'optimize' { ,@('-m', 'app.optimizer', $Request) }
    })
    foreach ($step in $steps) {
        & $py @step
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
} finally {
    Pop-Location
}
