# VMware VMnet reinstall - no user input required
$ErrorActionPreference = "Continue"
$vmwareDir = "F:\software\vmware"
$logFile = "$env:TEMP\vmware-net-repair.log"

function Log($msg) {
    $line = "[$(Get-Date -Format 'HH:mm:ss')] $msg"
    Add-Content -Path $logFile -Value $line
    Write-Host $line
}

Remove-Item $logFile -ErrorAction SilentlyContinue
Log "=== VMware VMnet Reinstall ==="

Set-Location $vmwareDir

Log "Installing drivers via drvInst64..."
$drvOut = & "$vmwareDir\drvInst64.exe" 2>&1
Log "drvInst64 output: $drvOut"

foreach ($inf in @("netadapter.inf", "netbridge.inf", "netuserif.inf")) {
    $path = Join-Path $vmwareDir $inf
    if (Test-Path $path) {
        $out = pnputil /add-driver $path /install 2>&1
        Log "pnputil $inf : $out"
    }
}

Log "Running vnetlib64 --updateAll..."
if (Test-Path "$vmwareDir\vnetlib64.exe") {
    $out = & "$vmwareDir\vnetlib64.exe" --updateAll 2>&1
    Log "vnetlib64: $out"
}

Log "Running VMnetDHCP -i..."
if (Test-Path "$vmwareDir\VMnetDHCP.exe") {
    $out = & "$vmwareDir\VMnetDHCP.exe" -i 2>&1
    Log "VMnetDHCP: $out"
}

Log "Starting services..."
foreach ($svc in @("VMAuthdService", "VMnetDHCP", "VMware NAT Service", "VMUSBArbService")) {
    Start-Service -Name $svc -ErrorAction SilentlyContinue
    $st = (Get-Service $svc -ErrorAction SilentlyContinue).Status
    Log "  $svc -> $st"
}

Start-Sleep -Seconds 5

Log "Enabling PnP devices..."
Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object {
    Log "  Device: $($_.FriendlyName) [$($_.Status)]"
    Enable-PnpDevice -InstanceId $_.InstanceId -Confirm:$false -ErrorAction SilentlyContinue
}

foreach ($name in @("VMware Network Adapter VMnet1", "VMware Network Adapter VMnet8")) {
    Enable-NetAdapter -Name $name -Confirm:$false -ErrorAction SilentlyContinue
    Enable-NetAdapterBinding -Name $name -ComponentID vmware_bridge -ErrorAction SilentlyContinue
    $a = Get-NetAdapter -Name $name -ErrorAction SilentlyContinue
    if ($a) { Log "  $name -> $($a.Status)" } else { Log "  $name -> NOT FOUND" }
}

Log "=== Final adapters ==="
Get-NetAdapter | Where-Object { $_.Name -like "*VMware*" } | ForEach-Object {
    Log "  $($_.Name) | $($_.Status) | $($_.MacAddress)"
}

Log "=== Done ==="
