$tags = @("ActivityManager", "WindowManager", "ViewRootImpl", "SystemServer", "InputDispatcher", "BatteryService", "WifiService", "PowerManagerService")
$pid = 1234
$tid = 5678
$logLines = @()

$currentTime = [DateTime]::Now
$activeFlows = @{}
$orderIds = 1..100 | ForEach-Object { "flow_{0:D3}" -f $_ }
$steps = @(
    "Order {0} started",
    "Transaction verified for {0}",
    "Payment for {0} processed",
    "Order {0} shipped",
    "Order {0} completed"
)
$stepTags = @("OrderEngine", "PaymentProcessor", "PaymentProcessor", "LogPulse", "LogPulse")

for ($i = 1; $i -le 1500; $i++) {
    $currentTime = $currentTime.AddMilliseconds((Get-Random -Minimum 50 -Maximum 500))
    $timeStr = $currentTime.ToString("MM-dd HH:mm:ss.fff")
    
    # Randomly start new flow
    if ((Get-Random -Minimum 0 -Maximum 100) -lt 5 -and $activeFlows.Count -lt 10) {
        $nextId = $orderIds | Where-Object { -not $activeFlows.ContainsKey($_) } | Select-Object -First 1
        if ($nextId) { $activeFlows[$nextId] = 0 }
    }
    
    # Progress existing flows
    if ((Get-Random -Minimum 0 -Maximum 100) -lt 15 -and $activeFlows.Count -gt 0) {
        $keys = $activeFlows.Keys | Get-Random -Count 1
        $stepIdx = $activeFlows[$keys]
        
        $logLines += "${timeStr}  ${pid}  ${tid} I $($stepTags[$stepIdx]): $($steps[$stepIdx] -f $keys)"
        
        if ($stepIdx -lt ($steps.Count - 1)) {
            # Random Failure for flow_007
            if ($keys -eq "flow_007" -and $stepIdx -eq 1) {
                $logLines += "${timeStr}  ${pid}  ${tid} E PaymentProcessor: Insufficient funds for ${keys}"
                $activeFlows.Remove($keys)
            } else {
                $activeFlows[$keys] = $stepIdx + 1
            }
        } else {
            $activeFlows.Remove($keys)
        }
        continue
    }

    # Crash at line 500
    if ($i -eq 500) {
        $logLines += "${timeStr}  ${pid}  ${tid} E AndroidRuntime: FATAL EXCEPTION: main"
        $logLines += "${timeStr}  ${pid}  ${tid} E AndroidRuntime: Process: com.antdev.logpulse, PID: ${pid}"
        $logLines += "${timeStr}  ${pid}  ${tid} E AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method 'void java.lang.String.trim()' on a null object reference"
        $logLines += "${timeStr}  ${pid}  ${tid} E AndroidRuntime: `t`tat com.antdev.logpulse.presentation.viewmodel.LogViewModel.onLogSelected(LogViewModel.kt:157)"
        $logLines += "${timeStr}  ${pid}  ${tid} E AndroidRuntime: `t`tat com.antdev.logpulse.presentation.viewmodel.LogViewModel`$jumpToLog`$1.invokeSuspend(LogViewModel.kt:164)"
        continue
    }

    # System Noise
    $tag = $tags | Get-Random
    $level = if ((Get-Random -Max 100) -lt 5) { "W" } elseif ((Get-Random -Max 100) -lt 2) { "E" } else { "D" }
    $msg = switch ($tag) {
        "ActivityManager" { "Displaying activity com.antdev.logpulse/.MainActivity: +450ms" }
        "WindowManager" { "Relayout Window{f3e4e5 m=false}: viewVisibility=0" }
        "ViewRootImpl" { "MSG_RESIZED: frame=[0,0][1400,900] ci=[0,0][0,0] vi=[0,0][0,0] or=1" }
        default { "Background maintenance task execution: $(Get-Random -Max 100)%" }
    }
    $logLines += "${timeStr}  ${pid}  ${tid} ${level} ${tag}: ${msg}"
}

$logLines | Out-File -FilePath "logs.txt" -Encoding utf8
Write-Host "Generated 1500+ lines to logs.txt"
