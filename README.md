# ParserNews

ParserNews is a research-first Java/Spring Boot backend for monitoring corporate
news and detecting public-company M&A, takeover, merger, and take-private
signals.

It is not a trading bot. It does not connect to brokers, wallets, exchanges, or
place orders. The current MVP only collects news, stores articles/events,
classifies signals with deterministic rules, and prepares human-reviewable
alerts.

## Current MVP

- Mock, historical JSON, and public RSS news input.
- Persistent URL deduplication.
- Manual scan endpoint and optional scan scheduler.
- Scan run history.
- Saved article API.
- M&A candidate API.
- Rule-based candidate scoring:
  - `candidateScore`
  - `candidateStrength`: `HIGH`, `MEDIUM`, `LOW`, `NONE`
  - `candidateReason`
- False-positive filtering for Senior Notes, bonds, debt tender offers,
  offerings, asset acquisitions, and other non-takeover noise.
- Candidate recompute/backfill for old local/dev data.
- Alert eligibility, preview, dry-run, manual queue, manual send foundation, and
  dispatch scheduler.
- Telegram notifier foundation, disabled by default.
- Backend operational status endpoint.
- H2 local database by default; PostgreSQL profile via Docker Compose.

## Safe Defaults

Local development is safe by default:

- Basic Auth is enabled by default
- `alerts.telegram.enabled=false`
- `alerts.dispatch.enabled=false`
- `scanner.monitoring.enabled=false`
- no real Telegram messages are sent unless explicitly enabled and configured
- no trading, broker, wallet, or exchange integration exists
- no real tokens should be committed

If Telegram is disabled, alert send/dispatch paths return no-op responses and do
not mark candidates as queued/sent.

## Basic Auth

The dashboard and API are protected by HTTP Basic Auth:

- `/`
- `/index.html`
- `/api/**`
- `/actuator/**`

Credentials are configured with environment variables:

```text
PARSERNEWS_AUTH_USERNAME
PARSERNEWS_AUTH_PASSWORD
```

Local H2 development defaults are:

```text
username: admin
password: admin
```

Docker Compose sets:

```text
username: admin
password: change-me
```

Change the password before exposing the app outside localhost, Tailscale, or a
private network. Do not commit real passwords.

CSRF is disabled intentionally because the static same-origin dashboard already
uses authenticated `POST` and `PATCH` API calls without CSRF tokens. CORS is not
opened.

## Requirements

- Java 24, as currently used by the project
- Maven Wrapper from this repository
- Optional: Docker for local container launch

## Run Tests

```powershell
.\mvnw.cmd test
```

## Start Local Mock Mode

```powershell
.\mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8080
```

The default local database is H2:

```text
data/parsernews-dev
```

Do not commit generated local database files.

## Start Live RSS Mode

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=live"
```

This reads configured public RSS feeds. It does not enable alert dispatch or
Telegram sending by itself.

Current live RSS coverage includes GlobeNewswire, PR Newswire, SEC press
releases, and TMX Newsfile public RSS feeds. TMX Newsfile is included through
its public last-25-stories feed and Banking/Financial Services industry feed.
Business Wire M&A is a useful candidate source, but it is not enabled yet
because the public M&A page does not expose a simple RSS URL in the current
scanner format; adding it should wait for either a stable RSS endpoint or a
small source-specific parser.

## Source Evaluation Notes

New sources should be added only when they are stable RSS feeds, compatible with
the current `RssNewsParser`, and likely to contain public-company M&A,
takeover, take-private, or activist proposal signals. Broad market news, market
data APIs, API-key products, and HTML-only pages should stay out of the RSS
parser until there is a focused module for them.

| Source | Classification | Current fit | M&A usefulness | Noise | Decision |
| --- | --- | --- | --- | --- | --- |
| Thomson Reuters IR RSS | Direct RSS source | Technically compatible RSS, but only Thomson Reuters investor-relations content | Low for broad scanner; useful only for Thomson Reuters itself | Low, but too narrow | Skip |
| yfinance | Market data library/API | Not RSS and Python-focused | Useful later for price snapshots, not news collection | N/A | Skip for RSS; possible future market-data module |
| Alpha Vantage | Market data API requiring key | Not RSS; requires API-key configuration | Useful later for prices, quotes, fundamentals | N/A | Skip for RSS; possible future market-data module |
| Trustnet RSS | Direct RSS source / RSS page | Some feeds are XML, but content is fund/research-oriented | Low for public-company takeover announcements | Medium/high | Skip |
| Investegate | HTML page requiring parser | Not a simple RSS feed for current parser | Potentially high for UK RNS/takeover notices | High without filtering | Future source-specific parser candidate |
| Marketaux | API requiring key | Not RSS; requires API integration | Potentially useful news API later | Depends on query | Skip for RSS; possible future API module |
| Business Journals syndication | Direct RSS page, but feed access returned 403 in local probe | Not safe for current unattended RSS polling | Medium; regional business deal coverage, but not focused on public-company takeovers | Medium/high | Skip |
| MarketScreener hot news | HTML page requiring parser | Not safe as RSS; local RSS-style probe returned 403 | Medium for market news, but not focused enough | High | Skip |
| PR Newswire RSS | Direct RSS source / RSS directory | Already configured for relevant categories, including Acquisitions/Mergers and Shareholder Activism | High | Medium | Keep existing; no broad expansion now |
| Feedspot M&A RSS list | RSS directory/catalog | Do not ingest Feedspot itself | Useful only for discovering primary sources | N/A | Document only |
| Feedspot Private Equity RSS list | RSS directory/catalog | Do not ingest Feedspot itself | Useful only for discovering primary sources | N/A | Document only |
| Feedspot Financial News RSS list | RSS directory/catalog | Do not ingest Feedspot itself | Broad financial-news discovery list | High if added blindly | Document only |
| SEC EDGAR APIs | SEC filing API / separate future module | Do not mix into `RssNewsParser` | High for official filings and tender/proxy events | Medium without form filtering | Future SEC module |

No new RSS feeds were added in this evaluation pass. The safe choice is to keep
the current focused RSS set and avoid adding broad/noisy feeds that would make
the dashboard harder to review.

### SEC Future Module

SEC monitoring should be implemented separately from RSS parsing. Two reasonable
future approaches:

1. CIK watchlist using the SEC submissions API.
2. EDGAR daily index scanner for recent filing discovery.

Interesting filing types for future M&A monitoring:

- `8-K`
- `SC 13D`
- `SC 13G`
- `SC TO-T`
- `SC TO-I`
- `DEFM14A`
- `PREM14A`
- `425`
- `S-4`

## Easy Local Launch

For the simplest Windows launch, double-click:

```text
run-live.bat
```

Or run it from a terminal:

```powershell
.\run-live.bat
```

It starts the live dashboard on:

```text
http://localhost:8080/
```

Use `Ctrl+C` in the console window to stop the server. Telegram and alert
dispatch remain disabled unless you configure them separately.

If port `8080` is busy, use the backup launcher:

```powershell
.\run-live-8081.bat
```

or run the PowerShell launcher directly:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-live.ps1 -Port 8081 -OpenBrowser
```

Do not run Docker mode on `8080` and local H2 mode on `8080` at the same time,
because both dashboards use the same port.

## Docker PostgreSQL Launch

Docker Compose runs the Spring Boot app plus PostgreSQL. The app starts with
the `live,postgres` profiles, Flyway applies database migrations, and Hibernate
validates the schema instead of creating it dynamically.

Quick start:

```powershell
.\run-docker.bat
```

Then open:

```text
http://localhost:8080/
```

Default Docker login:

```text
username: admin
password: change-me
```

On Windows, the easiest Docker/PostgreSQL launch is:

```powershell
.\run-docker.bat
```

This opens:

```text
http://localhost:8080/
```

Docker Basic Auth defaults:

```text
username: admin
password: change-me
```

Optional local overrides can be placed in `.env`. Start from `.env.example` and
keep real secrets out of git.

Build and run:

```powershell
docker compose up --build
```

Run in the background:

```powershell
docker compose up -d --build
```

View logs:

```powershell
docker compose logs -f
```

Stop:

```powershell
docker compose down
```

Or use:

```powershell
.\stop-docker.bat
```

Open the dashboard:

```text
http://localhost:8080/
```

Docker stores PostgreSQL data in the named Docker volume
`parsernews-postgres-data` and scan reports in the mounted local `./output`
directory. Telegram and alert dispatch remain disabled by default in
`docker-compose.yml`.

SEC watchlist scanning is also disabled by default. Docker Compose reads these
optional environment variables, or the same values from a local `.env` file:

```text
SEC_SCANNER_ENABLED=false
SEC_SCANNER_WATCHLIST=
SEC_SCANNER_MAX_FILINGS_PER_CIK=20
```

Example smoke-test watchlist:

```text
SEC_SCANNER_ENABLED=true
SEC_SCANNER_WATCHLIST=320193,789019,1318605
SEC_SCANNER_MAX_FILINGS_PER_CIK=20
```

These example CIKs are Apple (`320193`), Microsoft (`789019`), and Tesla
(`1318605`). They are smoke-test examples only, not investment
recommendations. SEC scanning is watchlist-only; the app does not scan all SEC
filings.

`stop-docker.bat` / `docker compose down` stops containers but does not delete
the PostgreSQL volume. `docker compose down -v` deletes database data and should
be used only for a full reset.

This is a simple local/server-deployment foundation, not production hardening.
Do not commit real credentials; the compose file uses safe local defaults only.

## MVP v1 Dashboard Workflow

For day-to-day review, use the dashboard instead of opening raw endpoints:

1. Open `http://localhost:8080/`.
2. Review Deal Groups first. This is the primary workflow and groups related
   RSS/SEC evidence into one deal-level review item.
3. Use Signal Inbox only when you need the raw RSS/SEC signal view.
4. Click Details on a signal or deal group to inspect evidence, warnings, deal
   terms, relevance, timing, SEC document signals, and manual review state.
5. Use Mark useful / Ignore / Reset review to build quality feedback.
6. Use Full Refresh when you want one manual research cycle:
   - RSS scan
   - SEC scan if enabled/configured
   - SEC document fetch
   - RSS candidate recompute
7. SEC scanner disabled or empty watchlist warnings are normal on a fresh
   install. They mean SEC scanning is safely off until you add CIKs.

RSS auto scan is separate from Full Refresh. In the live profile,
`scanner.monitoring.enabled=true` runs scheduled RSS polling. Full Refresh runs
only when you click the dashboard button unless optional Scheduled Full Refresh
is explicitly enabled. Telegram and alert dispatch remain disabled unless you
configure and manually use them.

The SEC Watchlist Manager lets you add/remove CIKs from the database without
editing `.env` or restarting Docker. Use the SEC Company Lookup box to search by
ticker or company name, for example `AAPL` or `Apple`, then add the result to
the DB watchlist. Manual CIK entry remains available.

Manual Telegram preview/send is available from Signal Inbox, but Telegram is
disabled by default. Disabled/not configured responses are safe no-ops and do
not send external messages.

## Smoke Test

After Docker is running, verify the core private deployment flow with:

```powershell
.\scripts\smoke-test.ps1
```

The script uses Basic Auth from environment variables or defaults to
`admin/change-me`. It checks:

- `GET /api/status`
- `GET /api/signals?limit=5`
- `GET /api/sec/status`
- `GET /api/articles/candidates/export.csv`

It does not run alert dispatch and does not send Telegram messages.

If the app runs on another port:

```powershell
.\scripts\smoke-test.ps1 -BaseUrl http://localhost:8081 -Username admin -Password admin
```

## Live RSS Smoke Check

Start the app in live mode first:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=live"
```

Then, in a second PowerShell window, run:

```powershell
.\scripts\live-smoke.ps1
```

The smoke script is a manual local verification helper, not a deterministic unit
test. It checks the current backend against real RSS sources and runs:

- `GET /api/status`
- `POST /api/scan`
- `GET /api/scan-runs`
- `GET /api/articles?limit=5`
- `GET /api/articles/candidates?limit=5`
- `POST /api/alerts/dry-run`
- `GET /api/status`

It does not require Telegram credentials, does not call alert dispatch, does not
send external notifications, and does not enable trading or broker behavior.
Telegram and alert dispatch should remain disabled for this smoke check.

If Windows blocks local PowerShell scripts, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\live-smoke.ps1
```

## PostgreSQL Mode

Default local PostgreSQL values used by Docker Compose:

```text
database: parsernews
user: parsernews
password: parsernews
```

The PostgreSQL profile reads these environment variables:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

SEC scanner settings can be provided either as Spring properties or environment
variables. Both forms are supported:

```text
sec.scanner.enabled / SEC_SCANNER_ENABLED
sec.scanner.watchlist / SEC_SCANNER_WATCHLIST
sec.scanner.max-filings-per-cik / SEC_SCANNER_MAX_FILINGS_PER_CIK
```

The dashboard also includes a DB-backed SEC Watchlist Manager. If database
watchlist entries exist, enabled DB entries are used for SEC scans and this is
treated as an explicit manual watchlist configuration. If the DB watchlist is
empty, the scanner falls back to `SEC_SCANNER_WATCHLIST`. This lets local Docker
deployments start safely with no SEC scanning, then add or remove CIKs from the
dashboard without editing `.env` or restarting containers.

The SEC Watchlist Manager also supports company lookup by ticker or company
name. It uses the official SEC company ticker mapping
(`https://www.sec.gov/files/company_tickers.json`) with the same SEC
User-Agent header as the submissions scanner. Lookup results are cached in
memory during app runtime so the app does not request the SEC mapping on every
search. Useful endpoints:

- `GET /api/sec/company-lookup?q=AAPL`
- `POST /api/sec/watchlist/from-lookup`

Manual local run against an existing PostgreSQL instance:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/parsernews"
$env:SPRING_DATASOURCE_USERNAME="parsernews"
$env:SPRING_DATASOURCE_PASSWORD="parsernews"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=live,postgres"
```

Quick local development can still use H2 with the normal live launcher:

```powershell
.\run-live.bat
```

## Main Manual Flow

Run a scan:

```powershell
curl.exe -u admin:change-me -X POST http://localhost:8080/api/scan
```

Check system status:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/status
```

View latest scan runs:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/scan-runs
```

View saved articles:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/articles
```

View detected M&A candidates:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/articles/candidates
```

View eligible alert candidates:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/alerts/candidates
```

Preview all current alert messages without sending:

```powershell
curl.exe -u admin:change-me -X POST http://localhost:8080/api/alerts/dry-run
```

Run one alert dispatch cycle manually:

```powershell
curl.exe -u admin:change-me -X POST http://localhost:8080/api/alerts/dispatch
```

With default config this returns a disabled/no-op response and sends nothing.

## Recommended Local Verification Flow

1. Start the app in mock mode:

```powershell
.\mvnw.cmd spring-boot:run
```

2. Confirm the backend is healthy:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/status
```

3. Trigger one scan:

```powershell
curl.exe -u admin:change-me -X POST http://localhost:8080/api/scan
```

4. Check scan history:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/scan-runs
```

5. Check saved articles and candidates:

```powershell
curl.exe -u admin:change-me http://localhost:8080/api/articles
curl.exe -u admin:change-me http://localhost:8080/api/articles/candidates
```

6. Backfill candidate metadata if using an old local database:

```powershell
curl.exe -u admin:change-me -X POST http://localhost:8080/api/admin/recompute-candidates
```

7. Preview alert text without sending:

```powershell
curl.exe -u admin:change-me -X POST http://localhost:8080/api/alerts/dry-run
```

8. Run manual dispatch and verify it is disabled/no-op by default:

```powershell
curl.exe -u admin:change-me -X POST http://localhost:8080/api/alerts/dispatch
```

## API Endpoints

### Scanner

```text
POST /api/scan
```

Runs one scanner cycle manually and returns the existing scan summary.

```text
GET /api/scan-runs
GET /api/scan-runs/{id}
```

Returns persisted scan run history and one scan run by id.

### Articles And Candidates

```text
GET /api/articles
GET /api/articles/{id}
GET /api/articles/candidates
```

Returns saved articles, one saved article by id, and saved M&A candidates.
List endpoints do not return huge full article text.

### Alerts

```text
GET /api/alerts/candidates
```

Returns alert-eligible candidates that are not already queued.

```text
GET /api/alerts/candidates/{id}/preview
```

Returns the formatted alert message for one eligible candidate without sending.

```text
POST /api/alerts/dry-run
```

Returns formatted alert previews for all current eligible/non-queued candidates.
Does not send messages and does not mark candidates queued.

```text
POST /api/alerts/candidates/{id}/queue
```

Marks one eligible candidate as queued for future alerting.

```text
POST /api/alerts/candidates/{id}/send
```

Runs the notifier flow for one eligible candidate. With Telegram disabled, this
returns a no-op result and does not mark the candidate queued. If a real notifier
is enabled and reports success, the candidate is marked queued/sent.

```text
POST /api/alerts/dispatch
```

Runs one alert dispatch cycle manually. With dispatch disabled, this returns a
disabled/no-op response and sends nothing.

### Unified Signal Inbox

```text
GET /api/signals
GET /api/signals/{sourceType}/{id}/telegram-preview
POST /api/signals/{sourceType}/{id}/send-telegram
```

`/api/signals` combines RSS candidates and SEC filing signals into one review
queue. `sourceType` is `RSS_NEWS` or `SEC_FILING`.

Telegram preview/send from this inbox is manual-only. The app sends nothing
unless the user explicitly clicks a send action and Telegram is enabled and
configured. Automatic alert dispatch remains controlled separately by
`alerts.dispatch.enabled` and is disabled by default.

### Admin / Maintenance

```text
POST /api/admin/recompute-candidates
```

Recomputes candidate score, strength, reason, and alert eligibility for already
persisted detected events. This is useful for old H2/dev databases where URL
dedupe prevents already-saved articles from being reprocessed.

It does not rescan RSS feeds, create duplicate articles, reset `alertQueuedAt`,
or make already queued candidates eligible again.

### Status

```text
GET /api/status
```

Returns a compact operational summary:

- `status`: `OK` or `WARN`
- scanner monitoring enabled
- alert dispatch enabled
- Telegram enabled
- Telegram configured true/false, without exposing token or chat id
- latest scan run summary
- article/candidate counts
- alert eligible and already queued counts

`WARN` is returned when the latest scan failed, or when monitoring is enabled
but no scan has ever run.

## Configuration Flags

### Scanner Monitoring

```properties
scanner.monitoring.enabled=false
scanner.monitoring.initial-delay-ms=60000
scanner.monitoring.poll-delay-ms=300000
```

Enables scheduled scanner polling when set to `true`.

### Full Refresh Scheduler

```properties
full-refresh.scheduler.enabled=false
full-refresh.scheduler.initial-delay-ms=120000
full-refresh.scheduler.fixed-delay-ms=900000

FULL_REFRESH_SCHEDULER_ENABLED=false
FULL_REFRESH_SCHEDULER_INITIAL_DELAY_MS=120000
FULL_REFRESH_SCHEDULER_FIXED_DELAY_MS=900000
```

Full Refresh is manual by default. When this optional scheduler is enabled, the
app periodically runs the same research cycle as the dashboard Full Refresh
button: RSS scan, SEC scan, SEC document fetch, and RSS candidate recompute. It
does not send Telegram messages and does not run alert dispatch.

Scheduler status is available in the dashboard Action Center and at:

```text
GET /api/admin/scheduler-status
```

### Telegram

```properties
parsernews.telegram.enabled=false
parsernews.telegram.bot-token=
parsernews.telegram.chat-id=

PARSERNEWS_TELEGRAM_ENABLED=false
PARSERNEWS_TELEGRAM_BOT_TOKEN=
PARSERNEWS_TELEGRAM_CHAT_ID=
```

Telegram is disabled by default. Do not commit real tokens or chat ids. Use
environment-specific local configuration if real manual sending is tested later.
The older internal `alerts.telegram.*` properties are still wired through these
safe parsernews settings for compatibility.

### Alert Dispatch

```properties
alerts.dispatch.enabled=false
alerts.dispatch.fixed-delay-ms=300000
alerts.dispatch.batch-size=5
```

Alert dispatch is disabled by default. When enabled, the scheduler processes up
to `alerts.dispatch.batch-size` eligible/non-queued candidates per cycle. A
candidate is marked queued/sent only when the notifier reports a real successful
send.

## Historical JSON Mode

Put old articles into:

```text
data/historical-news.json
```

Run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.news-file=file:data/historical-news.json"
```

Example item:

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

## Rule Tuning

Rule weights and thresholds live in:

```text
src/main/resources/analyzer-rules.json
```

Keep rule output explainable. Alerts and candidate records should continue to
show matched keywords, score, strength, and reason.

## Current Limitations

- No market data snapshots yet.
- No price charts yet.
- No statistical return analysis yet.
- No production auth.
- No AI/Ollama/OpenAI classifier yet.
- No trading, broker, wallet, or exchange integration.
- Telegram and alert dispatch are foundations only and disabled by default.

