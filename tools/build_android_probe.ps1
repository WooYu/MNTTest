param(
    [string]$ProjectDir = "android-probe-app",
    [string]$GradleHome = "D:\jobs\gradle",
    [string]$GradleUserHome = "D:\jobs\gradle\user-home",
    [string]$GradleVersion = "9.3.0",
    [string]$GradleZipName = "gradle-9.3.0-bin.zip"
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$project = Join-Path $root $ProjectDir
$gradleZip = Join-Path $GradleHome $GradleZipName
$gradleDir = Join-Path $GradleHome "gradle-$GradleVersion"
$gradleBat = Join-Path $gradleDir "bin\gradle.bat"

if (-not (Test-Path $gradleBat)) {
    New-Item -ItemType Directory -Force -Path $GradleHome | Out-Null
    if (-not (Test-Path $gradleZip)) {
        $downloadUrl = "https://services.gradle.org/distributions/$GradleZipName"
        Write-Host "Downloading $downloadUrl -> $gradleZip"
        Invoke-WebRequest -Uri $downloadUrl -OutFile $gradleZip
    }
    Expand-Archive -Path $gradleZip -DestinationPath $GradleHome -Force
}

New-Item -ItemType Directory -Force -Path $GradleUserHome | Out-Null
$env:GRADLE_USER_HOME = $GradleUserHome

Push-Location $project
try {
    & .\gradlew.bat --no-daemon assembleDebug
}
finally {
    Pop-Location
}
