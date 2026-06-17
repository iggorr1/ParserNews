param(
    [int]$Port = 8080,
    [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

$dashboardUrl = "http://localhost:$Port"

Write-Host "Starting ParserNews live profile..."
Write-Host "Dashboard: $dashboardUrl"
Write-Host "Telegram remains disabled unless configured separately."
Write-Host "Alert dispatch remains disabled unless configured separately."

if ($OpenBrowser) {
    Start-Process $dashboardUrl
}

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=live" "-Dspring-boot.run.arguments=--server.port=$Port"
