param(
    [string]$MinStatus = "WATCHLIST",
    [int]$MaxItems = 20
)

$ErrorActionPreference = "Stop"

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.source=rss --scanner.safety.real-web-parsing-enabled=true --scanner.console-min-status=$MinStatus --scanner.rss.max-items-per-feed=$MaxItems"
