# 双平板 MQTT 短测：监听 logcat，测试结束后拉取最新 summary 并解读 recv/perf 字段。
#
# 用法（先在平板上手动操作，再运行本脚本）：
#   1. 回显端 a4fbf4e7：角色=回显端 → 启动回显端，等到「回显端已就绪」
#   2. 探测端 50f08d16：Broker 选「广州测试 · 8.138.127.94」（须与回显一致）→ 开始测试
#   3. PC 上执行：
#        .\tools\watch_dual_probe_run.ps1
#
# 仅拉取并解读最近一次导出（不测中监听）：
#        .\tools\watch_dual_probe_run.ps1 -PullOnly
#
param(
    [string]$ProbeSerial = "50f08d16",
    [string]$EchoSerial = "a4fbf4e7",
    [int]$MaxWaitSec = 600,
    [string]$OutDir = "",
    [string]$RemoteDir = "/sdcard/Download/YunJuTongProbe",
    [switch]$PullOnly
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

if (-not $OutDir) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = Join-Path $RepoRoot "test-runs\watch_$stamp"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$probeLog = Join-Path $OutDir "probe.log"
$echoLog = Join-Path $OutDir "echo.log"
$metaFile = Join-Path $OutDir "meta.txt"

function Write-Section([string]$Title) {
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Invoke-Adb([string[]]$AdbArgs) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & adb @AdbArgs 2>&1 | ForEach-Object {
            if ($_ -is [System.Management.Automation.ErrorRecord]) {
                Write-Host $_.ToString()
            } else {
                Write-Host $_
            }
        }
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Get-LatestRunBase([string]$Serial) {
    $indexPath = Join-Path $OutDir "_index.json"
    Invoke-Adb @("-s", $Serial, "pull", "$RemoteDir/index.json", $indexPath) | Out-Null
    if (Test-Path $indexPath) {
        try {
            $index = Get-Content $indexPath -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($index.entries -and $index.entries.Count -gt 0) {
                return [string]$index.entries[0].base
            }
        } catch {
            Write-Host "  index.json 解析失败，改按目录名排序" -ForegroundColor Yellow
        }
    }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $lines = & adb -s $Serial shell "ls -1t '$RemoteDir' 2>/dev/null"
    } finally {
        $ErrorActionPreference = $prev
    }
    foreach ($line in ($lines -split "`n")) {
        $name = $line.Trim()
        if ($name -match '^probe_\d{8}_\d{6}_') {
            return $name
        }
    }
    return $null
}

function Resolve-SummaryPath([string]$RunDir, [string]$BaseName) {
    $candidates = @(
        (Join-Path $RunDir "summary.json")
        (Join-Path $RunDir "$BaseName\summary.json")
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    $found = Get-ChildItem -Path $RunDir -Recurse -Filter "summary.json" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($found) { return $found.FullName }
    $legacy = Get-ChildItem -Path $RunDir -Recurse -Filter "*_summary.json" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($legacy) { return $legacy.FullName }
    return (Join-Path $RunDir "summary.json")
}

function Pull-RunFolder([string]$Serial, [string]$BaseName) {
    $localRun = Join-Path $OutDir $BaseName
    New-Item -ItemType Directory -Force -Path $localRun | Out-Null
    $remoteRun = "$RemoteDir/$BaseName"
    Write-Host "  拉取 $remoteRun -> $localRun"
    Invoke-Adb @("-s", $Serial, "pull", $remoteRun, $localRun)
    return $localRun
}

function Show-SummaryReport([string]$SummaryPath) {
    if (-not (Test-Path $SummaryPath)) {
        Write-Host "  未找到 summary.json: $SummaryPath" -ForegroundColor Red
        return
    }
    $json = Get-Content $SummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host "  runId       : $($json.runId)"
    Write-Host "  broker      : $($json.host):$($json.port)"
    Write-Host "  count/pps   : $($json.count) / $($json.pps)"
    Write-Host "  sent/recv   : $($json.sent) / $($json.received)"
    Write-Host "  loss        : $([math]::Round($json.lossRate * 100, 2))%"
    Write-Host "  avg/p95 RTT : $($json.avgRttMs) / $($json.p95RttMs) ms"
    if ($json.perf) {
        $p = $json.perf
        Write-Host "  perf        : actual $($p.actualPps) pps (target $($p.targetPps)), lag $($p.maxSendLagMs) ms, belowTarget=$($p.belowTarget)"
    }
    if ($json.recv) {
        $r = $json.recv
        Write-Host "  recv        : lastSeq=$($r.lastRecvSeq), stall=$($r.recvStallDetected), stallMs=$($r.recvStallMs)"
        Write-Host "               echoDisconnected=$($r.echoDisconnectedSuspected), stoppedEarly=$($r.publishStoppedEarly)"
        if ($r.publishStoppedEarly) {
            Write-Host "               stoppedAtSeq=$($r.publishStoppedAtSeq), postStallPublish=$($r.postStallPublishCount)" -ForegroundColor Yellow
        }
        if ($r.catchUpResetCount -gt 0) {
            Write-Host "               catchUpReset=$($r.catchUpResetCount)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  recv        : (无 recv 块 — 可能为旧版 APK 或未导出)" -ForegroundColor Yellow
    }
}

function Show-LogHighlights([string]$Path, [string]$Label) {
    if (-not (Test-Path $Path)) {
        Write-Host "  无 $Label 日志" -ForegroundColor Yellow
        return
    }
    $text = Get-Content $Path -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if (-not $text) { return }

    Write-Host "  [$Label] runInternal / responder 启动:"
    Select-String -InputObject $text -Pattern "runInternal start:|responder start:" -AllMatches |
        ForEach-Object { $_.Matches } | ForEach-Object { Write-Host "    $($_.Value)" }

    $stall = Select-String -InputObject $text -Pattern "收包停滞|提前结束发包" -AllMatches
    if ($stall) {
        Write-Host "  [$Label] 停滞告警:" -ForegroundColor Yellow
        $stall | ForEach-Object { $_.Line.Trim() } | Select-Object -First 5 | ForEach-Object { Write-Host "    $_" }
    }

    $done = Select-String -InputObject $text -Pattern "done: sent=" -AllMatches
    if ($done) {
        Write-Host "  [$Label] 结束行:"
        $done | Select-Object -Last 1 | ForEach-Object { Write-Host "    $($_.Line.Trim())" }
    }

    $segments = Select-String -InputObject $text -Pattern "echo seq=\d+ rtt=.* out=" -AllMatches
    if ($segments) {
        $sample = $segments | Select-Object -First 3
        Write-Host "  [$Label] 分段 RTT 样例 (wall-clock / ns):"
        $sample | ForEach-Object { Write-Host "    $($_.Line.Trim())" }
    }

    $inbound = Select-String -InputObject $text -Pattern "echo stamp seq=.* inbound=" -AllMatches
    if ($inbound) {
        Write-Host "  [$Label] 回显 inbound 样例:"
        $inbound | Select-Object -First 3 | ForEach-Object { Write-Host "    $($_.Line.Trim())" }
    }

    $broken = Select-String -InputObject $text -Pattern "Broken pipe|responder exception" -AllMatches
    if ($broken) {
        Write-Host "  [$Label] 回显异常:" -ForegroundColor Red
        $broken | Select-Object -First 3 | ForEach-Object { Write-Host "    $($_.Line.Trim())" }
    }
}

# ── 说明 ──
Write-Section "操作提示"
$modeDesc = if ($PullOnly) { '进入仅拉取模式' } else { "开始监听(最长 ${MaxWaitSec}s)" }
Write-Host "  1. 回显端 ($EchoSerial)：角色=回显端 -> 启动回显端 -> 等到「回显端已就绪」"
Write-Host "  2. 探测端 ($ProbeSerial)：Broker 须与回显一致（建议 广州测试 8.138.127.94）-> 开始测试"
Write-Host "  3. 本脚本已$modeDesc"

$probeProc = $null
$echoProc = $null
$started = $false
$done = $false

if (-not $PullOnly) {
    Write-Section "清空 logcat 并开始采集"
    adb -s $ProbeSerial logcat -c | Out-Null
    adb -s $EchoSerial logcat -c | Out-Null

    $probeProc = Start-Process -FilePath "adb" -ArgumentList @(
        "-s", $ProbeSerial, "logcat", "-v", "threadtime",
        "ProbeApp:I", "ProbeApp:D", "ProbeApp:W", "ProbeApp:E", "*:S"
    ) -RedirectStandardOutput $probeLog -PassThru -WindowStyle Hidden

    $echoProc = Start-Process -FilePath "adb" -ArgumentList @(
        "-s", $EchoSerial, "logcat", "-v", "threadtime",
        "ProbeApp:I", "ProbeApp:D", "ProbeApp:W", "ProbeApp:E", "*:S"
    ) -RedirectStandardOutput $echoLog -PassThru -WindowStyle Hidden

    Write-Host "  日志 -> $OutDir"
    Write-Host "  请在平板上启动测试…"

    $deadline = (Get-Date).AddSeconds($MaxWaitSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        if (Test-Path $probeLog) {
            $tail = (Get-Content $probeLog -Tail 100 -ErrorAction SilentlyContinue) -join "`n"
            if ($tail -match "runInternal start:|probe loop:") { $started = $true }
            if ($tail -match "done: sent=") { $done = $true; break }
        }
        if (-not $started -and (Test-Path $echoLog)) {
            $et = (Get-Content $echoLog -Tail 40 -ErrorAction SilentlyContinue) -join "`n"
            if ($et -match "responder start:") { $started = $true }
        }
    }

    if ($started -and -not $done) {
        Write-Host "  检测到测试进行中，额外等待 45s 捕获结束行…" -ForegroundColor Yellow
        $extra = (Get-Date).AddSeconds(45)
        while ((Get-Date) -lt $extra) {
            Start-Sleep -Seconds 2
            $t = (Get-Content $probeLog -Tail 30 -ErrorAction SilentlyContinue) -join "`n"
            if ($t -match "done: sent=") { $done = $true; break }
        }
    }

    Start-Sleep -Seconds 1
    foreach ($p in @($probeProc, $echoProc)) {
        if ($p -and -not $p.HasExited) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        }
    }

    Write-Host "  started=$started  done=$done"
}

Write-Section "拉取最新导出"
$baseBefore = Get-LatestRunBase $ProbeSerial
Write-Host "  当前最新 run: $baseBefore"
Write-Host "  若测试刚结束，请在探测端结果页确认已自动导出，或点「再次导出」后重跑: .\tools\watch_dual_probe_run.ps1 -PullOnly"

if ($baseBefore) {
    $runDir = Pull-RunFolder $ProbeSerial $baseBefore
    $summary = Resolve-SummaryPath $runDir $baseBefore
    Show-SummaryReport $summary
} else {
    Write-Host "  未找到 probe_* 导出目录，请确认探测端已导出到 Download/YunJuTongProbe" -ForegroundColor Red
}

if (-not $PullOnly) {
    Write-Section "日志解读"
    Show-LogHighlights $probeLog "探测端"
    Show-LogHighlights $echoLog "回显端"
}

@(
    "probe_serial=$ProbeSerial"
    "echo_serial=$EchoSerial"
    "out_dir=$OutDir"
    "started=$started"
    "done=$done"
    "latest_base=$baseBefore"
    "ended=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
) | Set-Content -Path $metaFile -Encoding UTF8

Write-Section "完成"
Write-Host "  输出目录: $OutDir"
Write-Host "  完整日志: probe.log / echo.log"
if ($baseBefore) {
    $summaryPath = Resolve-SummaryPath (Join-Path $OutDir $baseBefore) $baseBefore
    Write-Host "  summary : $summaryPath"
}
