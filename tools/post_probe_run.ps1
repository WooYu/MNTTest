param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceId,
    [Parameter(Mandatory = $true)]
    [string]$SceneId,
    [string]$AbbaRound = "",
    [string]$OutDir = "",
    [string]$RemoteDir = "/sdcard/Download/YunJuTongProbe",
    [switch]$SkipPull,
    [switch]$AllRuns
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $RepoRoot "test-runs\$SceneId"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not $SkipPull) {
    Write-Host ">> 步骤 1/5：拉取导出文件"
    & (Join-Path $PSScriptRoot "pull_probe_runs.ps1") -DeviceId $DeviceId -OutDir $OutDir -RemoteDir $RemoteDir
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} else {
    Write-Host ">> 跳过拉取（-SkipPull）"
}

$pyArgs = @(
    (Join-Path $PSScriptRoot "post_probe_run.py"),
    "--dir", $OutDir,
    "--scene", $SceneId
)

if (-not [string]::IsNullOrWhiteSpace($AbbaRound)) {
    $pyArgs += @("--abba-round", $AbbaRound)
}

if (-not $AllRuns) {
    $pyArgs += "--latest-only"
}

Write-Host ""
Write-Host ">> 步骤 2–5：校验 / 归档 / 场景表 / 过载检测"
python @pyArgs
$code = $LASTEXITCODE

Write-Host ""
if ($code -eq 0) {
    Write-Host "测后处理完成。"
    if (-not [string]::IsNullOrWhiteSpace($AbbaRound)) {
        Write-Host "  ABBA 副本: $OutDir\${AbbaRound}_summary.json"
    }
    Write-Host "  场景表:    $RepoRoot\test-runs\scenario_manifest.csv"
} else {
    Write-Host "测后处理完成，但存在校验不一致（exit $code）。请检查上方输出。"
}

exit $code
