# Project Context

## Stack

- Java 24
- Spring Boot 3.5
- Maven Wrapper
- Spring Web
- Spring Data JPA / Hibernate
- H2 for local development
- PostgreSQL via Docker Compose for persistent database mode
- Plain static HTML/CSS/JavaScript UI served by Spring Boot

## Project Goal

Research-first M&A / takeover event intelligence platform.

The app collects corporate news, classifies acquisition / takeover /
going-private events, filters common false positives, stores detected events,
and shows explainable alerts.

This is not a trading bot.

## Folder Structure

- `src/main/java/com/parsernews/config` - Spring configuration properties, rule loading, RSS settings, safety settings.
- `src/main/java/com/parsernews/model` - In-memory scanner models such as `NewsEvent`, `AnalysisResult`, `EventType`, `EventStatus`, scan reports.
- `src/main/java/com/parsernews/parser` - News input readers: mock data, historical JSON, public RSS feeds.
- `src/main/java/com/parsernews/service` - Core logic: scanning pipeline, rule-based analysis, false-positive filters, alerts, reports, persistence coordination.
- `src/main/java/com/parsernews/persistence` - JPA entities, repositories, stored event/source/article models, local schema compatibility helpers.
- `src/main/java/com/parsernews/web` - REST endpoints for reports and saved detected events.
- `src/main/resources/static` - Browser UI. Currently `index.html` only.
- `src/main/resources` - App properties, analyzer rules, mock data.
- `src/test/java` - Unit tests for parser, analyzer, reporting, safety, filtering.
- `data` - Local H2 database and historical input data. Do not commit generated DB files.
- `output` - Generated JSON/CSV scan reports. Do not treat as source of truth.
- `scripts` - Helper scripts, including live RSS runner.

## How To Run

Default mock mode:

```powershell
.\mvnw.cmd spring-boot:run
```

Live RSS mode:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=live"
```

PostgreSQL mode:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"
```

Historical JSON mode:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--scanner.news-file=file:data/historical-news.json"
```

Run tests:

```powershell
.\mvnw.cmd test
```

Open UI:

```text
http://localhost:8080
```

## Backend / Frontend

Backend is the Spring Boot app in `src/main/java/com/parsernews`.

Frontend is a simple static page:

- `src/main/resources/static/index.html`

There is no separate frontend framework, build step, Node app, or frontend dev
server yet.

## Auth

There is no authentication or user system yet.

Current endpoints are local development endpoints only. If this becomes
internet-facing later, auth and access control must be added before deployment.

## Core Task Logic

Main flow:

1. `StockScannerApplication`
2. `NewsScannerService`
3. `NewsSourceParser`
4. `RuleBasedNewsAnalyzer`
5. `FalsePositiveFilter`
6. `EventPersistenceService`
7. `AlertService`
8. `ReportExportService`

Most classification behavior lives in:

- `RuleBasedNewsAnalyzer`
- `FalsePositiveFilter`
- `src/main/resources/analyzer-rules.json`

Stored event API lives in:

- `EventController`
- `ReportController`

## Current Features

- Mock news scanning.
- Historical JSON scanning.
- Public HTTPS RSS scanning.
- Scheduled RSS polling for live monitoring.
- Manual scan endpoint at `POST /api/scan`.
- Persistent scan run history at `GET /api/scan-runs`.
- Saved article read API at `GET /api/articles` and `GET /api/articles/candidates`.
- Deterministic candidate scoring with score, strength, and reason.
- Alert eligibility and queue foundation at `GET /api/alerts/candidates`.
- Alert message preview and dry-run at `GET /api/alerts/candidates/{id}/preview` and `POST /api/alerts/dry-run`.
- Telegram notifier foundation is disabled by default and guarded by `alerts.telegram.enabled`.
- Local/admin candidate backfill at `POST /api/admin/recompute-candidates`.
- Rule-based event classification.
- False-positive filtering for debt tender offers, senior notes, bonds, offerings, asset acquisitions, reverse mergers, and other non-takeover noise.
- Event statuses: `IGNORED`, `WATCHLIST`, `MANUAL_REVIEW`, `IMPORTANT`, `HIGH_PRIORITY_SIGNAL`.
- Deal term extraction from text:
  - ticker when RSS/article text contains an exchange label;
  - offer price;
  - payment type;
  - buyer;
  - stated premium.
- Console alerts with matched keywords and extracted terms.
- JSON/CSV report export.
- Saved news sources, raw articles, and detected events.
- Saved scan run summaries with trigger type, status, counts, and failure message.
- H2 local database.
- PostgreSQL profile with Docker Compose.
- REST API for reports and saved events.
- Browser UI for scan results, saved events, filtering, manual review, and extracted terms.
- Optional full article text fetching for whitelisted RSS article hosts.

## Current Limitations

- Full article fetching is limited to whitelisted hosts and likely M&A candidate teasers.
- Ticker can be `UNKNOWN` when RSS text does not include an exchange/ticker label.
- Premium is only extracted when stated in the article. It is not yet calculated from market price.
- No market data snapshots yet.
- No price charts yet.
- No Telegram alerts yet.
- No AI/Ollama/OpenAI analysis yet.
- No production auth.
- No trading, broker, wallet, or exchange integration.

## Do Not Touch Without Explicit Request

- Do not add real-money trading, broker, wallet, or exchange integration.
- Do not add order execution.
- Do not add AI classification as the primary analyzer yet.
- Do not remove rule explainability: alerts must show matched keywords and score.
- Do not scrape arbitrary websites without a whitelist and safety checks.
- Do not delete local data or reports unless the user asks.
- Do not replace the simple UI with a large frontend framework unless the user asks.
- Do not loosen false-positive filters for debt, notes, bonds, offerings, or asset acquisitions without tests.

## Working Rules For Future Changes

- First read `PROJECT_CONTEXT.md`.
- Do not scan the whole repository unless necessary.
- Work only with files related to the current task.
- Do not refactor unrelated code.
- Do not change formatting or style outside edited lines.
- Before editing, explain the minimal plan and list target files.
- After editing, show the diff summary and commands/tests run.

## Best Next Steps

1. Fetch full article text for whitelisted sources after RSS discovery.
2. Improve ticker extraction with source-specific article parsing or company-to-ticker lookup.
3. Add market data snapshots around publication time.
4. Add price charts after news events.
5. Build a historical validation workflow for old articles.
6. Add scheduled RSS polling.
