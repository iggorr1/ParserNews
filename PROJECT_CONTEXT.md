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
docker compose up --build
```

Docker Compose runs the app with `live,postgres`, starts PostgreSQL, and applies
Flyway migrations. Quick local live mode still uses H2 unless the PostgreSQL
profile is explicitly enabled.

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

Form login protects the dashboard and API (`SecurityConfig`). There is **no** HTTP Basic:
unauthenticated `/api/**` and `/actuator/**` requests get `401` so `fetch()` can handle them,
while browser navigation to HTML pages redirects (`302`) to `/login.html`.

Credentials come from:

- `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` (role `ADMIN`)
- `APP_VIEWER_USERNAME` / `APP_VIEWER_PASSWORD` (optional, role `VIEWER`)

Toggle with `PARSERNEWS_AUTH_ENABLED`. Change the password before exposing the app outside
localhost or a private network.

`/api/export/**` is exempt from the session login (GET and POST) because it is gated by its own
shared key in the `X-API-Key` header — see "Data export API" below.

## Core Task Logic

There are **two ingestion sources** that converge on one review/alert pipeline. Any task about
classification must be clear which stage it targets.

### 1. RSS ingestion (rule-based)

1. `StockScannerApplication` / `NewsMonitoringScheduler` / `FullRefreshService`
2. `NewsScannerService`
3. `NewsSourceParser`
4. `RuleBasedNewsAnalyzer` + `FalsePositiveFilter`
5. `EventPersistenceService` — saves **every** article and its `DetectedEventEntity`,
   including `IGNORED` ones (the discard pile is retained, not thrown away)
6. Only non-`IGNORED` events continue downstream

Most rule classification lives in `RuleBasedNewsAnalyzer`, `FalsePositiveFilter`, and
`src/main/resources/analyzer-rules.json`.

### 2. SEC ingestion (content-based)

- `SecDiscoveryService` — EDGAR `getcurrent` feed, by form type
- SEC full-text search (EFTS) — finds early 8-K deal announcements **by content**, which the
  keyword rules miss
- `SecCompanyLookupService` — authoritative SEC company name -> ticker/CIK resolution

### 3. Shared downstream (grouping -> AI -> alert/export)

1. `DealGroupingService` — groups related RSS + SEC signals into one deal group
2. `DealGroupAiReviewService` (OpenAI) — **secondary** verdict/confidence/reason/risk flags;
   identifies target vs acquirer (tickers resolved via SEC, not by the model); runs a second
   price-verification pass (`verifyOfferPrice`) that confirms/corrects the per-share offer price
   and grounds it in a quote, with a deterministic guard that the number appears in the source
3. `StockPriceService` — freshest available price (pre/post-market aware) + `asOf` timestamp;
   powers the merger-arb spread
4. `AutoDealGroupDispatchService` -> Telegram (HIGH/MEDIUM, AI-gated, freshness-gated)
5. `ExportController` — read-only feed for an external analytics consumer

Rules remain the primary classifier for the RSS path and must stay explainable. The OpenAI
layer judges grouped candidates; it does not replace the rules.

Stored event API lives in `EventController` and `ReportController`.

## Data export API

`GET /api/export/deals` and `POST /api/export/deals/{groupKey}/recheck`, authenticated by the
`X-API-Key` header (`EXPORT_API_KEY`, kept only in `.env`, never in git). Returns an
analytics-ready shape: target/acquirer + SEC-resolved tickers, `tickerConfidence`, offer price
with `priceStatus`/`priceQuote` from the AI check, `currentPrice` + `priceAsOf`, `spreadPct`,
AI verdict. `recheck` forces a fresh AI re-extraction for one deal.

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
- Telegram notifier foundation is disabled by default, guarded by safe env/config, and can be temporarily configured from the dashboard with backend memory-only runtime settings.
- Alert dispatch scheduler and manual dispatch are disabled by default and guarded by `alerts.dispatch.enabled`.
- Operational status summary at `GET /api/status`.
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
- SEC EDGAR discovery (`getcurrent` by form type) and SEC full-text search (EFTS) that finds
  early 8-K deal announcements by content rather than keywords.
- SEC company -> ticker/CIK resolution (`SecCompanyLookupService`).
- Deal grouping: related RSS + SEC signals collapse into one deal group.
- OpenAI AI review of deal groups (secondary, never the primary classifier): verdict,
  confidence, reason, risk flags, target/acquirer identification, offer price extraction.
- AI price-check pass: verifies/corrects the per-share offer price, grounds it in an exact
  quote, and downgrades to `UNVERIFIED` when the number is absent from the source.
- Live price + merger-arb spread (`StockPriceService`), pre/post-market aware, with `priceAsOf`.
- Telegram dispatch of HIGH/MEDIUM deal groups with AI verdict, price, spread, and inline
  Useful/Ignore buttons.
- Token-authenticated read-only export API for an external analytics consumer.
- Feed health monitoring at `GET /api/feed-health`, latency metric at `GET /api/latency`.

## Current Limitations

- Full article fetching is limited to whitelisted hosts and likely M&A candidate teasers.
- Ticker can still be `UNKNOWN` when neither the AI target name nor SEC lookup resolves it.
- Prices come from Yahoo (unofficial, no SLA): near-real-time during the regular session,
  otherwise the freshest pre/post-market bar; `priceAsOf` always states the moment, and a
  5-minute cache applies. Not an exchange-grade real-time feed.
- The real universe of tradable US-public-target deals is only ~1-5/day. Do not design for
  "hundreds of deals a day" — that expectation is wrong and was measured.
- The RSS `IGNORED` discard pile (~60-100 articles/day) is retained but nothing re-reads it,
  so recall there is unmeasured.
- No price charts yet.
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

## Local LLM host (Ollama)

A local LLM (Ollama) runs on a separate GPU host on the private network. Its address is
deployment config, not source: it lives in `.env` / the audit config, never here.

Ollama has **no authentication**, so bind it to the private overlay interface only — never
`0.0.0.0`, which would expose an open LLM API to the whole LAN.

Models: `qwen2.5:7b` (use this) and `qwen2.5:3b`. Measured on real headlines from this project,
7b scored 4/4 and 3b only 3/4 — 3b confused an *equity* tender offer with a *debt* tender offer,
the exact distinction that matters here. Ollama's JSON-Schema `format` enforces structure and
types but **not** value ranges (`minimum`/`maximum` are ignored), so validate ranges in Java.

Nothing in the app calls Ollama yet.

## Best Next Steps

1. Recall net: run the local LLM over RSS articles the rules **rejected** (`IGNORED`), to catch
   deals the keyword rules miss, then resolve the target via SEC to check it is public. This is
   the stage that currently has no LLM at all — do **not** add a local "second opinion" on
   already-flagged candidates, where the OpenAI review already gives an independent verdict
   from a stronger model.
2. Build a historical validation workflow for old articles (measure recall, not just precision).
3. Add price charts after news events.
4. Evaluate a real-time news wire (e.g. Oruk SSE) — measured as the biggest latency/coverage
   lever, but needs their endpoint, auth, and a paid subscription.
