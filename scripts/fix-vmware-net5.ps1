$log = "$env:TEMP\vmware-quickfix.log"
Remove-Item $log -ErrorAction SilentlyContinue

function L($m){ $m | Out-File $log -Append; Write-Host $m }

L "=== Quick fix: restart + enable VMnet ==="

Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object {
    L "Processing $($_.FriendlyName) [$($_.Status)] id=$($_.InstanceId)"
    pnputil /restart-device $_.InstanceId 2>&1 | ForEach-Object { L "  restart: $_" }
    Disable-PnpDevice -InstanceId $_.InstanceId -Confirm:$false -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    Enable-PnpDevice -InstanceId $_.InstanceId -Confirm:$false -ErrorAction SilentlyContinue
    $d = Get-PnpDevice -InstanceId $_.InstanceId
    L "  after enable: $($d.Status)"
}

Start-Sleep -Seconds 2

foreach ($n in @("VMware Network Adapter VMnet1","VMware Network Adapter VMnet8")) {
    Enable-NetAdapter -Name $n -Confirm:$false -ErrorAction SilentlyContinue
    Enable-NetAdapterBinding -Name $n -ComponentID vmware_bridge -ErrorAction SilentlyContinue
    $a = Get-NetAdapter -Name $n -ErrorAction SilentlyContinue
    L "NetAdapter $n -> $(if($a){$a.Status}else{'NOT FOUND'})"
}

L "=== Final ==="
Get-NetAdapter | Where-Object { $_.Name -like "*VMware*" } | ForEach-Object { L "$($_.Name) | $($_.Status)" }
Get-PnpDevice | Where-Object { $_.FriendlyName -like "*VMware Virtual Ethernet*" } | ForEach-Object { L "PnP: $($_.FriendlyName) | $($_.Status)" }
