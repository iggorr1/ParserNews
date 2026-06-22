param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = $env:PARSERNEWS_AUTH_USERNAME,
    [string]$Password = $env:PARSERNEWS_AUTH_PASSWORD
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Username)) {
    $Username = "admin"
}

if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = "change-me"
}

function New-AuthHeader {
    $pair = "${Username}:${Password}"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    return @{ Authorization = "Basic $encoded" }
}

function Invoke-SmokeJson {
    param(
        [string]$Name,
        [string]$Path
    )

    $url = "$BaseUrl$Path"
    Write-Host ""
    Write-Host "== $Name =="
    Write-Host "GET $url"
    $result = Invoke-RestMethod -Method GET -Uri $url -Headers $script:Headers -TimeoutSec 30
    $result | ConvertTo-Json -Depth 8
}

function Invoke-SmokeCsv {
    param(
        [string]$Name,
        [string]$Path
    )

    $url = "$BaseUrl$Path"
    Write-Host ""
    Write-Host "== $Name =="
    Write-Host "GET $url"
    $auth = "${Username}:${Password}"
    $content = & curl.exe -sS -u $auth $url
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with curl exit code $LASTEXITCODE"
    }
    $firstLine = (($content -split "`r?`n") | Select-Object -First 1)
    if ($firstLine -notlike "id,*") {
        throw "$Name did not return the expected CSV header. First line: $firstLine"
    }
    Write-Host "CSV OK. Header: $firstLine"
}

$script:Headers = New-AuthHeader

try {
    Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/status" -Headers $script:Headers -TimeoutSec 10 | Out-Null
} catch {
    Write-Error "ParserNews is not responding at $BaseUrl or Basic Auth failed. Start Docker with .\run-docker.bat and check credentials. Defaults are admin / change-me."
    exit 1
}

Write-Host "ParserNews smoke test"
Write-Host "Base URL: $BaseUrl"
Write-Host "Username: $Username"
Write-Host "This script does not send Telegram messages and does not run alert dispatch."

Invoke-SmokeJson "System status" "/api/status"
Invoke-SmokeJson "Signal Inbox" "/api/signals?limit=5"
Invoke-SmokeJson "SEC status" "/api/sec/status"
Invoke-SmokeCsv "Candidates CSV export" "/api/articles/candidates/export.csv?limit=5"

Write-Host ""
Write-Host "Smoke test finished."
