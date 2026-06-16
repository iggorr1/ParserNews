param(
    [string]$MinStatus = "WATCHLIST",
    [int]$MaxItems = 20,
    [switch]$KeepUiServer
)

$ErrorActionPreference = "Stop"

$runArguments = "--scanner.source=rss --scanner.safety.real-web-parsing-enabled=true --scanner.console-min-status=$MinStatus --scanner.rss.max-items-per-feed=$MaxItems"

if (-not $KeepUiServer) {
    $runArguments = "$runArguments --spring.main.web-application-type=none"
}

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=$runArguments"
