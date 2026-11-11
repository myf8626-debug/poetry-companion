$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath "$PSScriptRoot\.venv\Scripts\python.exe")) {
    throw 'Voice Python environment is missing. Run tools/voice-service/setup.ps1 on this computer first.'
}
& "$PSScriptRoot\.venv\Scripts\python.exe" "$PSScriptRoot\local\run_local.py"
exit $LASTEXITCODE
