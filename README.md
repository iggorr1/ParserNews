# OTC Take-Private News Scanner

Console MVP for scanning mock or historical news articles with explainable keyword rules.

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

## Run Historical Articles

Put old articles into `data/historical-news.json`, then run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.news-file=file:data/historical-news.json"
```

Each historical item can include optional labels:

```json
{
  "ticker": "ABCD",
  "companyName": "Example Corp.",
  "headline": "Example Corp. Enters Definitive Merger Agreement",
  "body": "Shareholders will receive $5.00 per share in cash.",
  "source": "Old Article Archive",
  "sourceUrl": "https://example.com/article",
  "expectedEventType": "TAKE_PRIVATE_CONFIRMED",
  "expectedStatus": "IMPORTANT",
  "notes": "Manual label for rule tuning."
}
```

When expected labels are present, console alerts print whether the analyzer matched them.
At the end of each run, the app also prints a scan summary with total articles,
duplicates skipped, labeled articles, and matched or mismatched expected results.
It also writes reports to:

```text
output/scan-results.json
output/scan-results.csv
```

You can override paths:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.news-file=file:data/historical-news.json --scanner.report-json=output/history.json --scanner.report-csv=output/history.csv"
```

## Tune Rules

Edit keyword weights and status thresholds in:

```text
src/main/resources/analyzer-rules.json
```
