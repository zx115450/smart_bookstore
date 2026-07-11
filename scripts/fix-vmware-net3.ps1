$ErrorActionPreference = "Continue"
$vmwareDir = "F:\software\vmware"
$log = "$env:TEMP\vmware-drvinst.log"
Remove-Item $log -ErrorAction SilentlyContinue

function Log($m) { Add-Content $log $m; Write-Host $m }

Log "=== drvInst64 install VMnet adapters ==="
Set-Location $vmwareDir

foreach ($id in @("*VMnetAdapter8", "*VMnetAdapter1")) {
    Log "Installing $id ..."
    $p = Start-Process -FilePath "$vmwareDir\drvInst64.exe" -ArgumentList @("installRootDriver", "0", $id, "$vmwareDir\netadapter.inf", "0") -Wait -PassThru -NoNewWindow
    Log "  exit code: $($p.ExitCode)"
}

Log "Running vnetlib64 --updateAll"
$p2 = Start-Process -FilePath "$vmwareDir\vnetlib64.exe" -ArgumentList "--updateAll" -Wait -PassThru -NoNewWindow
Log "  exit code: $($p2.ExitCode)"

Start-Sleep -Seconds 3

Log "=== PnP devices ==="
Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object {
    Log "  $($_.FriendlyName) | $($_.Status)"
    if ($_.Status -ne 'OK') {
        Enable-PnpDevice -InstanceId $_.InstanceId -Confirm:$false -ErrorAction SilentlyContinue
    }
}

Log "=== Net adapters ==="
Get-NetAdapter | Where-Object { $_.Name -like "*VMware*" } | ForEach-Object {
    Log "  $($_.Name) | $($_.Status)"
    Enable-NetAdapter -Name $_.Name -Confirm:$false -ErrorAction SilentlyContinue
    Enable-NetAdapterBinding -Name $_.Name -ComponentID vmware_bridge -ErrorAction SilentlyContinue
}

reg query "HKLM\SOFTWARE\WOW6432Node\VMware, Inc.\VMnetLib\VMnetConfig\vmnet8\Adapter" 2>&1 | ForEach-Object { Log $_ }
reg query "HKLM\SOFTWARE\WOW6432Node\VMware, Inc.\VMnetLib\VMnetConfig\vmnet1\Adapter" 2>&1 | ForEach-Object { Log $_ }

Log "=== Done ==="
