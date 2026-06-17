param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Invoke-SmokeStep {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path
    )

    $url = "$BaseUrl$Path"
    Write-Host ""
    Write-Host "== $Name =="
    Write-Host "$Method $url"
    if ($Method -eq "POST") {
        curl.exe -sS -X POST $url
    } else {
        curl.exe -sS $url
    }
    Write-Host ""
}

try {
    Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/status" -TimeoutSec 5 | Out-Null
} catch {
    Write-Error "ParserNews is not responding at $BaseUrl. Start the app first, for example: .\mvnw.cmd spring-boot:run `"-Dspring-boot.run.profiles=live`""
    exit 1
}

Write-Host "ParserNews live smoke check"
Write-Host "Base URL: $BaseUrl"
Write-Host "Telegram and alert dispatch should remain disabled for this smoke check."
Write-Host "This script does not send external notifications."

Invoke-SmokeStep "Initial status" "GET" "/api/status"
Invoke-SmokeStep "Manual scan" "POST" "/api/scan"
Invoke-SmokeStep "Scan runs" "GET" "/api/scan-runs"
Invoke-SmokeStep "Latest articles" "GET" "/api/articles?limit=5"
Invoke-SmokeStep "Latest candidates" "GET" "/api/articles/candidates?limit=5"
Invoke-SmokeStep "Alert dry-run" "POST" "/api/alerts/dry-run"
Invoke-SmokeStep "Final status" "GET" "/api/status"

Write-Host ""
Write-Host "Smoke check finished. Review the JSON output above for WARN status, scan errors, candidates, and dry-run alert previews."
