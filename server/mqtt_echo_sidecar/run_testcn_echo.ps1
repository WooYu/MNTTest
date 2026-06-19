param(
    [string]$BrokerIp = "",
    [int]$Port = 1883,
    [string]$Env = "testcn",
    [string]$SendSn = "",
    [string]$ReceiveSn = "",
    [string]$ReceivePwd = "",
    [string]$ReceiveMac = "",
    [int]$MsgCount = 100000,
    [switch]$InstallDeps
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$script = Join-Path $root "References\MqttTestPython\mqtt_recieve_both.py"

if (-not $InstallDeps) {
    $missingArgs = @()
    if ([string]::IsNullOrWhiteSpace($BrokerIp)) { $missingArgs += "-BrokerIp" }
    if ([string]::IsNullOrWhiteSpace($SendSn)) { $missingArgs += "-SendSn" }
    if ([string]::IsNullOrWhiteSpace($ReceiveSn)) { $missingArgs += "-ReceiveSn" }
    if ([string]::IsNullOrWhiteSpace($ReceivePwd)) { $missingArgs += "-ReceivePwd" }
    if ([string]::IsNullOrWhiteSpace($ReceiveMac)) { $missingArgs += "-ReceiveMac" }
    if ($missingArgs.Count -gt 0) {
        Write-Host "Missing required MQTT test args: $($missingArgs -join ', ')"
        Write-Host "Example: .\server\mqtt_echo_sidecar\run_testcn_echo.ps1 -BrokerIp <broker_ip> -SendSn <send_sn> -ReceiveSn <receive_sn> -ReceivePwd <sn_pwd> -ReceiveMac <mac>"
        exit 1
    }
}

if ($InstallDeps) {
    python -m pip install requests loguru paho-mqtt pycryptodome
}
else {
    $check = @'
import importlib.util
deps = [
    ("requests", "requests"),
    ("loguru", "loguru"),
    ("paho.mqtt.client", "paho-mqtt"),
    ("Crypto", "pycryptodome"),
]
missing = [pkg for mod, pkg in deps if importlib.util.find_spec(mod.split(".")[0]) is None]
if missing:
    raise SystemExit("Missing Python deps: " + ", ".join(missing))
'@
    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $result = $check | python - 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $oldErrorActionPreference
    if ($exitCode -ne 0) {
        Write-Host $result
        Write-Host "Run first: .\server\mqtt_echo_sidecar\run_testcn_echo.ps1 -InstallDeps"
        exit 1
    }
}

python $script `
    --ip $BrokerIp `
    --port $Port `
    --env $Env `
    --send_sn $SendSn `
    --recieve_sn $ReceiveSn `
    --sn_pwd $ReceivePwd `
    --sn_mac_address $ReceiveMac `
    --msg_count $MsgCount
