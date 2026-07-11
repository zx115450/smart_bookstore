# VMware VMnet repair script - requires Administrator
$ErrorActionPreference = "Continue"
$vmwareDir = "F:\software\vmware"

Write-Host "=== [1/6] Stop VMware services ===" -ForegroundColor Cyan
$services = @("VMAuthdService", "VMnetDHCP", "VMware NAT Service", "VMUSBArbService")
foreach ($svc in $services) {
    Stop-Service -Name $svc -Force -ErrorAction SilentlyContinue
    Write-Host "  $svc -> $((Get-Service $svc -ErrorAction SilentlyContinue).Status)"
}

Write-Host "`n=== [2/6] Remove broken VMnet adapters ===" -ForegroundColor Cyan
$devices = Get-PnpDevice | Where-Object { $_.FriendlyName -like "VMware Virtual Ethernet Adapter for VMnet*" }
foreach ($dev in $devices) {
    Write-Host "  Removing: $($dev.FriendlyName) [$($dev.Status)]"
    pnputil /remove-device $dev.InstanceId /force 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        # fallback: disable then remove via devcon-like approach
        Disable-PnpDevice -InstanceId $dev.InstanceId -Confirm:$false -ErrorAction SilentlyContinue
    }
}

Write-Host "`n=== [3/6] Reinstall VMnet drivers ===" -ForegroundColor Cyan
Set-Location $vmwareDir
if (Test-Path "$vmwareDir\drvInst64.exe") {
    & "$vmwareDir\drvInst64.exe" 2>&1
    Write-Host "  drvInst64.exe executed"
} else {
    Write-Host "  drvInst64.exe not found!" -ForegroundColor Red
}

# Reinstall via pnputil
$infs = @("netadapter.inf", "netbridge.inf", "netuserif.inf")
foreach ($inf in $infs) {
    $path = Join-Path $vmwareDir $inf
    if (Test-Path $path) {
        pnputil /add-driver $path /install 2>&1
        Write-Host "  Installed: $inf"
    }
}

Write-Host "`n=== [4/6] Restore virtual network config ===" -ForegroundColor Cyan
if (Test-Path "$vmwareDir\vnetlib64.exe") {
    & "$vmwareDir\vnetlib64.exe" --updateAll 2>&1
    Write-Host "  vnetlib64 --updateAll executed"
}
if (Test-Path "$vmwareDir\VMnetDHCP.exe") {
    & "$vmwareDir\VMnetDHCP.exe" -i 2>&1
    Write-Host "  VMnetDHCP -i executed"
}

Write-Host "`n=== [5/6] Start services & enable adapters ===" -ForegroundColor Cyan
foreach ($svc in $services) {
    Start-Service -Name $svc -ErrorAction SilentlyContinue
    Write-Host "  $svc -> $((Get-Service $svc -ErrorAction SilentlyContinue).Status)"
}

Start-Sleep -Seconds 3
Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object {
    Enable-PnpDevice -InstanceId $_.InstanceId -Confirm:$false -ErrorAction SilentlyContinue
}

foreach ($name in @("VMware Network Adapter VMnet1", "VMware Network Adapter VMnet8")) {
    Enable-NetAdapter -Name $name -Confirm:$false -ErrorAction SilentlyContinue
    Enable-NetAdapterBinding -Name $name -ComponentID vmware_bridge -ErrorAction SilentlyContinue
    $adapter = Get-NetAdapter -Name $name -ErrorAction SilentlyContinue
    if ($adapter) {
        Write-Host "  $name -> Status: $($adapter.Status)"
    } else {
        Write-Host "  $name -> NOT FOUND" -ForegroundColor Yellow
    }
}

Write-Host "`n=== [6/6] Final status ===" -ForegroundColor Cyan
Get-NetAdapter | Where-Object { $_.Name -like "*VMware*" } | Format-Table Name, Status, MacAddress -AutoSize
Write-Host "`nVMnet IP addresses:"
ipconfig | Select-String -Pattern "VMnet|IPv4" -Context 1,0

Write-Host "`nDone. Press Enter to close..."
Read-Host
