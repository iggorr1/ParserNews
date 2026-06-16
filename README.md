# M&A Event Intelligence Platform

Research-first Spring Boot project for collecting corporate news, detecting M&A /
takeover / going-private events, filtering false positives, and storing events for
later historical market-movement analysis.

## Safety Scope

This project is a research/news scanner only.

It does not:

- connect to brokers
- connect to wallets
- connect to exchanges
- execute trades
- place orders
- scrape arbitrary HTML pages

Startup safety flags are disabled by default in `src/main/resources/application.properties`.
If trading, broker, wallet, or exchange flags are enabled, the app fails fast.
The only internet mode currently allowed is public RSS reading, and it must be enabled explicitly.

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

Open the local UI:

```text
http://localhost:8080
```

The app stores raw articles and detected events. By default it uses a local H2
database file for easy development. For PostgreSQL, start Docker and use the
`db` profile:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"
```

PostgreSQL defaults:

```text
database: parsernews
user: parsernews
password: parsernews
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
output/mismatches.csv
```

You can override paths:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.news-file=file:data/historical-news.json --scanner.report-json=output/history.json --scanner.report-csv=output/history.csv"
```

To print only more serious alerts to console while still exporting all rows:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.news-file=file:data/historical-news.json --scanner.console-min-status=WATCHLIST"
```

Allowed console status filters are `IGNORED`, `WATCHLIST`, `MANUAL_REVIEW`,
`IMPORTANT`, and `HIGH_PRIORITY_SIGNAL`.

## Run Public RSS Feed

The first internet mode reads public RSS/XML feeds only. It does not scrape article pages.

Default RSS feeds:

```text
https://www.globenewswire.com/RssFeed/subjectcode/27-Mergers%20and%20Acquisitions/feedTitle/GlobeNewswire%20-%20Mergers%20and%20Acquisitions
https://www.globenewswire.com/RssFeed/subjectcode/17-Financing%20Agreements/feedTitle/GlobeNewswire%20-%20Financing%20Agreements
https://www.globenewswire.com/RssFeed/subjectcode/23-Joint%20Venture/feedTitle/GlobeNewswire%20-%20Joint%20Venture
https://www.globenewswire.com/RssFeed/subjectcode/37-Restructuring%202f%20Recapitalization/feedTitle/GlobeNewswire%20-%20Restructuring%20%2C%20Recapitalization
https://www.globenewswire.com/RssFeed/subjectcode/5-Bankruptcy/feedTitle/GlobeNewswire%20-%20Bankruptcy
https://www.globenewswire.com/RssFeed/subjectcode/57-Changes%20In%20Share%20Capital%20And%20Votes/feedTitle/GlobeNewswire%20-%20Changes%20In%20Share%20Capital%20And%20Votes
https://www.globenewswire.com/RssFeed/subjectcode/61-Corporate%20Action/feedTitle/GlobeNewswire%20-%20Corporate%20Action
https://www.globenewswire.com/RssFeed/subjectcode/72-Press%20Releases/feedTitle/GlobeNewswire%20-%20Press%20Releases
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/acquisitions-mergers-and-takeovers-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/bankruptcy-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/stock-offering-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/joint-ventures-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/financing-agreements-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/private-placement-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/restructuring-recapitalization-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/shareholder-activism-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/shareholder-meetings-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/venture-capital-list.rss
https://www.prnewswire.com/rss/news-releases/financial-services-latest-news/banking-financial-services-list.rss
https://www.prnewswire.com/rss/news-releases/general-business-latest-news/contracts-list.rss
https://www.prnewswire.com/rss/news-releases/general-business-latest-news/corporate-expansion-list.rss
https://www.prnewswire.com/rss/all-news-releases-from-PR-newswire-news.rss
https://www.sec.gov/news/pressreleases.rss
```

Run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.source=rss --scanner.safety.real-web-parsing-enabled=true --scanner.console-min-status=WATCHLIST --scanner.rss.max-items-per-feed=10"
```

Run with the live profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=live"
```

In IntelliJ IDEA, set Active profiles to:

```text
live
```

Then run `StockScannerApplication` and open:

```text
http://localhost:8080
```

The UI shows source and status breakdowns. Use `Signals only` to hide rows
where no positive or negative keyword matched.
Use `Saved events` to view persisted detected events from the database.
Use `Historical only` with `Saved events` to focus on imported old articles.
Saved events also include manual review controls. You can correct the target
ticker, mark whether the event is a valid deal or a false positive, and save a
short note for later rule tuning.

Score can still be `0` for real articles. That means the article was read,
but none of the configured acquisition, take-private, offering, bankruptcy,
or risk keywords appeared in the headline or RSS description.

`WATCHLIST`, `MANUAL_REVIEW`, and `IMPORTANT` are intentionally strict. Broad
M&A headlines such as `Company A acquires assets` or `Company B to acquire
private company C` are not enough. A row needs confirmed-deal signals such as:

- definitive agreement / definitive merger agreement
- to be acquired by / will be acquired by
- shareholders or stockholders will receive consideration
- per-share cash or stock consideration
- premium to market price
- going-private or take-private language

False positives such as Senior Notes, bond tender offers, asset acquisitions,
brand acquisitions, reverse mergers, financings, and public offerings stay
ignored for takeover-opportunity purposes.

`HIGH_PRIORITY_SIGNAL` is reserved for confirmed shareholder-deal language where
the scanner can also extract stronger terms such as offer price plus cash
consideration or a stated premium. The scanner does not yet calculate premium
from live market prices; for now it only stores the premium stated in the article.

Use custom HTTPS RSS feeds:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.source=rss --scanner.safety.real-web-parsing-enabled=true --scanner.rss.urls=https://example.com/feed.xml"
```

Or use the helper script:

```powershell
.\scripts\run-live-rss.ps1
```

By default the helper script refreshes `output/scan-results.json` once and exits.
If the UI is already open, refresh `http://localhost:8080` after the script finishes.
To keep a dedicated UI server running from the RSS command:

```powershell
.\scripts\run-live-rss.ps1 -KeepUiServer
```

If Windows blocks local PowerShell scripts, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-live-rss.ps1
```

With options:

```powershell
.\scripts\run-live-rss.ps1 -MinStatus IGNORED -MaxItems 50
```

## First Live Version Status

Ready now:

- read public HTTPS RSS feeds
- analyze headlines and RSS descriptions with explainable rules
- filter debt tender false positives such as Senior Notes or bonds
- store news sources, raw articles, and detected events
- expose persisted events at `GET /api/events`
- manually validate saved events in the UI
- extract deal terms such as offer price, payment type, buyer, ticker, and stated premium
- run with PostgreSQL using the `db` profile and Docker Compose
- use default public RSS feeds from GlobeNewswire and PR Newswire
- print console alerts
- view latest results in a browser at `http://localhost:8080`
- export JSON, CSV, and mismatch CSV reports
- run historical labeled datasets locally
- block broker, wallet, exchange, and trading integrations

Still missing for a stronger first production-like version:

- persistent duplicate detection across runs beyond URL hash storage
- more real historical examples for tuning
- better ticker/company extraction from RSS items
- more RSS sources, such as PR Newswire categories
- scheduled repeated runs
- market data snapshots after events
- statistical return analysis

## Tune Rules

Edit keyword weights and status thresholds in:

```text
src/main/resources/analyzer-rules.json
```

## Stored Event API

```text
GET /api/events
GET /api/events?status=WATCHLIST
GET /api/events?type=ACQUISITION
GET /api/events?sourceType=HISTORICAL
PATCH /api/events/{id}/review
```

Review payload:

```json
{
  "targetTicker": "OPEN",
  "validationStatus": "VALID_DEAL",
  "reviewNotes": "Confirmed public-company cash merger."
}
```

Validation statuses are `UNREVIEWED`, `VALID_DEAL`, `FALSE_POSITIVE`,
`NOT_PUBLIC_COMPANY`, and `TOO_LATE`.

Current schema is created by JPA:

- `news_sources`
- `news_articles`
- `detected_events`

This is intentionally small. Market data snapshots and statistical analysis come
after the event data model is stable.
