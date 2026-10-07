$ErrorActionPreference = 'Stop'
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
& (Join-Path $PSScriptRoot 'gradlew.bat') @args
exit $LASTEXITCODE
