param([switch]$VoiceOnly)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
if (-not (Test-Path -LiteralPath '.venv\Scripts\python.exe')) {
    & python -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw 'Creating the Python virtual environment failed.' }
}
& '.\.venv\Scripts\python.exe' -m pip install -r local/requirements.lock.txt
if ($LASTEXITCODE -ne 0) { throw 'Installing voice dependencies failed.' }
$configureArgs = @('local/configure_local.py')
if ($VoiceOnly) { $configureArgs += '--voice-only' }

& '.\.venv\Scripts\python.exe' @configureArgs
if ($LASTEXITCODE -ne 0) { throw 'Configuring private local credentials failed.' }
& '.\.venv\Scripts\python.exe' local/setup_asr.py
if ($LASTEXITCODE -ne 0) { throw 'Downloading the local ASR model failed.' }
} finally { Pop-Location }
