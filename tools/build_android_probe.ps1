param(
    [string]$ProjectDir = "android-probe-app",
    [string]$GradleZip = "D:\Android\AndroidGradle\gradle-8.3-all.zip"
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$project = Join-Path $root $ProjectDir
$gradleHome = Join-Path $root ".gradle-local\gradle-8.3"
$gradleBat = Join-Path $gradleHome "bin\gradle.bat"

if (-not (Test-Path $gradleBat)) {
    if (-not (Test-Path $GradleZip)) {
        throw "Gradle zip not found: $GradleZip"
    }
    $localDir = Join-Path $root ".gradle-local"
    New-Item -ItemType Directory -Force $localDir | Out-Null
    Expand-Archive -Path $GradleZip -DestinationPath $localDir -Force
}

Push-Location $project
try {
    & $gradleBat --no-daemon assembleDebug
}
finally {
    Pop-Location
}
