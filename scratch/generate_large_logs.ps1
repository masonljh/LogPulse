
$levels = @("V", "D", "I", "W", "E", "A")
$tags = @("System", "Network", "UI", "Database", "Auth", "Worker")
$messages = @(
    "Initializing component...",
    "Connection established to server.",
    "User clicked on button.",
    "Fetching data from database...",
    "Synchronization complete.",
    "Updating cache.",
    "Timeout occurred while waiting for response.",
    "Invalid credentials provided.",
    "Resource not found.",
    "Starting background task #$i",
    "Processing item ID: $($i % 1000)",
    "Memory usage: $($i * 5 + 1024) KB"
)

$targetFile = "large_logs.txt"
if (Test-Path $targetFile) { Remove-Item $targetFile }

$totalLines = 150000
$batchSize = 10000
$buffer = New-Object System.Collections.Generic.List[string]

Write-Host "Generating $totalLines lines to $targetFile..."

# Use a specific start time to increment from
$baseDate = Get-Date -Format "MM-dd"
$baseTime = Get-Date "2026-04-17 18:00:00"

for ($i = 1; $i -le $totalLines; $i++) {
    $currentTime = $baseTime.AddMilliseconds($i * 10)
    $timestamp = $currentTime.ToString("MM-dd HH:mm:ss.fff")
    
    $logPid = 1000 + ($i % 500)
    $logTid = 5000 + ($i % 1000)
    $logLevel = $levels[$i % $levels.Count]
    $logTag = $tags[$i % $tags.Count]
    $logMsg = $messages[$i % $messages.Count]
    
    # MM-DD HH:MM:SS.mmm PID TID Level Tag: Message
    $buffer.Add("${timestamp}  ${logPid}  ${logTid} ${logLevel} ${logTag}: ${logMsg}")
    
    if ($i % $batchSize -eq 0) {
        $buffer | Out-File -FilePath $targetFile -Append -Encoding utf8
        $buffer.Clear()
        Write-Host "Generated $i lines..."
    }
}

if ($buffer.Count -gt 0) {
    $buffer | Out-File -FilePath $targetFile -Append -Encoding utf8
}

Write-Host "Done! Generated $totalLines lines in $targetFile."
