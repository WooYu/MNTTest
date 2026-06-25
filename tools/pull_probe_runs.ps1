param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceId,
    [string]$OutDir = "",
    [string]$RemoteDir = "/sdcard/Download/YunJuTongProbe"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..")) "test-runs\pull_$stamp"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "Pulling probe runs from $DeviceId ..."
Write-Host "  remote: $RemoteDir"
Write-Host "  local : $OutDir"

adb -s $DeviceId pull $RemoteDir $OutDir 2>&1 | Write-Host

$files = Get-ChildItem -Path $OutDir -Recurse -Include samples.csv, summary.json, *_summary.json -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "Pulled $($files.Count) data file(s):"
foreach ($f in $files) {
    Write-Host "  $($f.FullName)"
}

Write-Host ""
Write-Host "Next: verify a run pair with:"
Write-Host "  python tools/verify_probe_run.py <samples.csv> <summary.json>"
