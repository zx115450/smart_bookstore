$log = "$env:TEMP\vmware-enable.log"
Remove-Item $log -ErrorAction SilentlyContinue

"=== Enable VMware adapters ===" | Out-File $log

Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object {
    "Device: $($_.FriendlyName) [$($_.Status)] InstanceId=$($_.InstanceId)" | Out-File $log -Append
    pnputil /remove-device $_.InstanceId /force 2>&1 | Out-File $log -Append
}

Start-Sleep -Seconds 2

Set-Location "F:\software\vmware"
& ".\drvInst64.exe" installRootDriver 0 *VMnetAdapter1 "F:\software\vmware\netadapter.inf" 0 2>&1 | Out-File $log -Append
& ".\drvInst64.exe" installRootDriver 0 *VMnetAdapter8 "F:\software\vmware\netadapter.inf" 0 2>&1 | Out-File $log -Append
& ".\vnetlib64.exe" --updateAll 2>&1 | Out-File $log -Append

Start-Sleep -Seconds 3

Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object {
    Enable-PnpDevice -InstanceId $_.InstanceId -Confirm:$false -ErrorAction SilentlyContinue
    "After enable: $($_.FriendlyName) -> $((Get-PnpDevice -InstanceId $_.InstanceId).Status)" | Out-File $log -Append
}

foreach ($n in @("VMware Network Adapter VMnet1","VMware Network Adapter VMnet8")) {
    Enable-NetAdapter -Name $n -Confirm:$false -ErrorAction SilentlyContinue
    Enable-NetAdapterBinding -Name $n -ComponentID vmware_bridge -ErrorAction SilentlyContinue
    $a = Get-NetAdapter -Name $n -ErrorAction SilentlyContinue
    "NetAdapter $n -> $(if($a){$a.Status}else{'NOT FOUND'})" | Out-File $log -Append
}

"=== ipconfig VMnet ===" | Out-File $log -Append
ipconfig | Out-File $log -Append
"=== DONE ===" | Out-File $log -Append
