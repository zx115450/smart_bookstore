$log = "$env:TEMP\vmware-msi-repair.log"
Remove-Item $log -ErrorAction SilentlyContinue

"=== VMware MSI Repair started $(Get-Date) ===" | Out-File $log

$productCode = "{40EB739C-B694-40E3-8F80-631209827A5D}"
$msi = "C:\Program Files (x86)\Common Files\VMware\InstallerCache\$productCode.msi"

"Product: $productCode" | Out-File $log -Append
"MSI: $msi exists=$(Test-Path $msi)" | Out-File $log -Append

# Stop services
foreach ($svc in @("VMAuthdService","VMnetDHCP","VMware NAT Service","VMUSBArbService")) {
    Stop-Service $svc -Force -ErrorAction SilentlyContinue
    "$svc stopped" | Out-File $log -Append
}

"Running msiexec repair..." | Out-File $log -Append
$p = Start-Process msiexec.exe -ArgumentList "/i", $productCode, "REINSTALL=ALL", "REINSTALLMODE=vomus", "/qn", "/norestart" -Wait -PassThru -NoNewWindow
"msiexec exit code: $($p.ExitCode)" | Out-File $log -Append

Start-Sleep -Seconds 5

foreach ($svc in @("VMAuthdService","VMnetDHCP","VMware NAT Service","VMUSBArbService")) {
    Start-Service $svc -ErrorAction SilentlyContinue
}

Set-Location "F:\software\vmware"
& ".\vnetlib64.exe" --updateAll 2>&1 | Out-File $log -Append

Start-Sleep -Seconds 3

"=== Adapters after repair ===" | Out-File $log -Append
Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object {
    "$($_.FriendlyName) | $($_.Status)" | Out-File $log -Append
}
Get-NetAdapter | Where-Object { $_.Name -like "*VMware*" } | ForEach-Object {
    "$($_.Name) | $($_.Status)" | Out-File $log -Append
}

"=== DONE $(Get-Date) ===" | Out-File $log -Append
