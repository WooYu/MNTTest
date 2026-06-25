# Dual-device live logcat capture (probe + echo responder)
# Usage: .\tools\capture_dual_probe_logs.ps1

param(
    [string]$ProbeSerial = "50f08d16",
    [string]$EchoSerial = "a4fbf4e7",
    [int]$MaxWaitSec = 180,
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = Join-Path $repo "tools\_live_logs\$stamp"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$probeLog = Join-Path $OutDir "probe.log"
$echoLog = Join-Path $OutDir "echo.log"
$metaFile = Join-Path $OutDir "meta.txt"

$meta = @(
    "probe_serial=$ProbeSerial"
    "echo_serial=$EchoSerial"
    "started=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
)
$meta | Set-Content -Path $metaFile -Encoding UTF8

Write-Host "Clearing logcat buffers..."
adb -s $ProbeSerial logcat -c | Out-Null
adb -s $EchoSerial logcat -c | Out-Null

$probeProc = Start-Process -FilePath "adb" -ArgumentList @(
    "-s", $ProbeSerial, "logcat", "-v", "threadtime",
    "ProbeApp:I", "ProbeApp:D", "ProbeApp:W", "ProbeApp:E",
    "Choreographer:I", "art:I", "*:S"
) -RedirectStandardOutput $probeLog -PassThru -WindowStyle Hidden

$echoProc = Start-Process -FilePath "adb" -ArgumentList @(
    "-s", $EchoSerial, "logcat", "-v", "threadtime",
    "ProbeApp:I", "ProbeApp:D", "ProbeApp:W", "ProbeApp:E",
    "Choreographer:I", "art:I", "*:S"
) -RedirectStandardOutput $echoLog -PassThru -WindowStyle Hidden

Write-Host "Capturing -> $OutDir"
Write-Host "Start echo + probe test on tablets (FIELD 1000/10pps). Waiting up to ${MaxWaitSec}s ..."

$deadline = (Get-Date).AddSeconds($MaxWaitSec)
$started = $false
$done = $false
$tail = @()

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if (Test-Path $probeLog) {
        $tail = @(Get-Content $probeLog -Tail 80 -ErrorAction SilentlyContinue)
        $probeText = ($tail -join "`n")
        if ($probeText -match "probe loop:|runInternal start:") { $started = $true }
        if ($probeText -match "done: sent=") { $done = $true; break }
    }
    if (-not $started -and (Test-Path $echoLog)) {
        $etail = @(Get-Content $echoLog -Tail 40 -ErrorAction SilentlyContinue)
        $echoText = ($etail -join "`n")
        if ($echoText -match "responder start:") { $started = $true }
    }
}

if ($started -and -not $done) {
    Write-Host "Test running, waiting extra 30s for done line..."
    $extra = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $extra) {
        Start-Sleep -Seconds 2
        $tailText = (Get-Content $probeLog -Tail 20 -ErrorAction SilentlyContinue) -join "`n"
        if ($tailText -match "done: sent=") { $done = $true; break }
    }
}

Start-Sleep -Seconds 1
foreach ($p in @($probeProc, $echoProc)) {
    if ($p -and -not $p.HasExited) {
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    }
}

Add-Content -Path $metaFile -Encoding UTF8 -Value "ended=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Add-Content -Path $metaFile -Encoding UTF8 -Value "started_detected=$started"
Add-Content -Path $metaFile -Encoding UTF8 -Value "done_detected=$done"
Add-Content -Path $metaFile -Encoding UTF8 -Value "probe_log=$probeLog"
Add-Content -Path $metaFile -Encoding UTF8 -Value "echo_log=$echoLog"

Write-Host "Capture finished. started=$started done=$done"
Write-Host "Analyze: python tools/analyze_dual_probe_logs.py --dir $OutDir"
