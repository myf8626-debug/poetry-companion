$ErrorActionPreference = 'Stop'
$taskConfig = Join-Path $env:USERPROFILE '.config/poetry-companion/backend.properties'
$taskJar = Join-Path $PSScriptRoot 'backend/target/poetry-companion-0.1.0.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Build first: mvn -f backend/pom.xml package' }
if (Test-Path -LiteralPath $taskConfig) {
    $taskUri = ([System.Uri]::new($taskConfig)).AbsoluteUri
    & java -jar $taskJar "--spring.config.additional-location=$taskUri"
} else {
    & java -jar $taskJar
}
exit $LASTEXITCODE
