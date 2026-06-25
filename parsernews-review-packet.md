# ParserNews Review Packet

- **Generated:** 2026-06-25T13:32:37.776821484Z

## App Status

- **health:** OK
- **scannerMonitoringEnabled:** true
- **alertDispatchEnabled:** false
- **telegramEnabled:** false
- **telegramConfigured:** false

### Latest Scan

- **id:** 1190
- **status:** SUCCESS
- **startedAt:** 2026-06-25T13:31:43.142725Z
- **finishedAt:** -
- **triggerType:** MANUAL
- **totalFetched:** 0
- **candidatesFound:** 0
- **duplicatesSkipped:** 0

### Counts

- **savedArticles:** 4673
- **detectedCandidates:** 52
- **highCandidates:** 43
- **mediumCandidates:** 9
- **lowCandidates:** 0
- **alertEligible:** 3
- **alertQueued:** 0

## Scheduler Status

- **rssMonitoringEnabled:** true
- **fullRefreshSchedulerEnabled:** false
- **fullRefreshSchedulerRunning:** false
- **lastScheduledFullRefreshStartedAt:** -
- **lastScheduledFullRefreshFinishedAt:** -
- **lastScheduledFullRefreshSuccess:** -
- **nextExpectedFullRefreshAt:** -
- **message:** Full Refresh runs only when you click the button.

### Latest Scheduled RSS Scan

- **id:** 1189
- **status:** SUCCESS
- **startedAt:** 2026-06-25T13:26:20.025702Z
- **finishedAt:** 2026-06-25T13:26:27.897533Z

## OpenAI Status

- **enabled:** false
- **configured:** false
- **keySource:** NONE
- **keyMasked:** -
- **model:** gpt-4.1-mini
- **maxInputChars:** 12000
- **message:** OpenAI AI Review is disabled.

## Telegram Status

- **enabled:** false
- **configured:** false
- **tokenSource:** NONE
- **tokenMasked:** -
- **chatIdSource:** NONE
- **chatIdMasked:** -
- **dispatchEnabled:** false
- **message:** Telegram is disabled; no external messages will be sent.

## Deal Group Quality Stats

- **totalGroups:** 30
- **pendingGroups:** 30
- **usefulGroups:** 0
- **ignoredGroups:** 0
- **highPriorityGroups:** 24
- **alertLikeGroups:** 1
- **groupedEvidenceTotal:** 42
- **averageEvidencePerGroup:** 1.40

### Review reasons

- none

### By relevance

- NOT_TRADABLE: 13
- PRIVATE_COMPANY_ACQUISITION: 6
- LAW_FIRM_OR_SHAREHOLDER_ALERT: 4
- UNKNOWN: 2
- PUBLIC_STOCK_MERGER: 1

### By tradability

- NOT_TRADABLE: 21
- MEDIUM: 2
- LOW: 3

### By stage

- INITIAL_ANNOUNCEMENT: 5
- RUMOR_OR_EXPLORATION: 2
- DEFINITIVE_AGREEMENT: 10
- UNKNOWN: 5
- LITIGATION_OR_LAW_FIRM_UPDATE: 4

### By timing

- EARLY: 17
- UNKNOWN: 5
- NOISE: 4

### By priority

- HIGH: 24
- MEDIUM: 4
- LOW: 2

## AI Review Summary

- **uniqueGroupsReviewed:** 7
- **totalAiReviewsSaved:** 10
- **duplicateHistoricalReviewsIgnored:** 3
- **goodSignalCount:** 1
- **notTradableCount:** 3
- **privateCompanyCount:** 2
- **falsePositiveCount:** 0
- **needsHumanReviewCount:** 1
- **unknownCount:** 0
- **highConfidenceCount:** 7
- **mediumConfidenceCount:** 0
- **lowConfidenceCount:** 0

### Latest AI Reviews

- target-cik:1926599 | target-cik:1926599 | NOT_TRADABLE / HIGH | The deal group does not specify a tradable buyer and involves a share redemption event rather than a straightforward acquisition with a cash or fixed-price offer. Although the target is public, the absence of a buyer ticker and the nature of the event make this less relevant as a useful M&A research signal for trading review.
- target-cik:1782107 | Onconetix Highlights Realbotix’s Launch of Humanoid Robot and AI Teachers Assistant Pilot Program in New York State School District | NEEDS_HUMAN_REVIEW / HIGH | The deal group lacks a clear buyer with ticker or cik information and does not explicitly mention a cash or fixed-price offer, making it an incomplete and unclear M&A signal for trading or deeper research purposes.
- target-cik:1551152 | target-cik:1551152 | GOOD_SIGNAL / HIGH | The deal involves a public, tradable target (AbbVie Inc.) with a cash acquisition, and it is at an initial announcement stage, making it an early and relevant M&A research signal. Multiple related signals and SEC filings support the validity and timing of this deal group.
- target-cik:2077709 | target-cik:2077709 | NOT_TRADABLE / HIGH | The target company is not tradable as it is a private technical consulting firm. The buyer ROC's details are incomplete and there is no indication of a public, tradable target or fixed-price offer. The signal is primarily a law firm/shareholder alert related to litigation or noise, not an actionable M&A tradable event.
- names:platinum equity:tangent technologies | PLATINUM EQUITY TO ACQUIRE TANGENT TECHNOLOGIES | PRIVATE_COMPANY / HIGH | The target, Tangent Technologies, is a private company and not tradable. Although the acquisition is at the definitive agreement stage and early enough, the lack of a tradable public target reduces the usefulness for tradable M&A research signals.
- rss:194 | rss:194 | PRIVATE_COMPANY / HIGH | The deal involves a private company acquisition with no public ticker or CIK information and tradability is low.
- target-cik:1531031 | Esquire Financial Holdings, Inc. and Signature Bancorporation Inc. Receive Stockholder Approvals for Merger | NOT_TRADABLE / HIGH | The deal involves a final exchange ratio announcement with no public buyer ticker and is marked as not tradable, likely indicating limited tradability or insufficient public market information.

## Source Quality Audit

- **totalConfiguredSources:** 25
- **KEEP:** 0
- **NEEDS_REVIEW:** 3
- **DISABLE:** 22
- **strictCandidateCountTotal:** 0

| sourceName | fetched | candidates | strict | noise | recommendation | errors |
|---|---:|---:|---:|---:|---|---:|
| GlobeNewswire Mergers and Acquisitions | 0 | 0 | 0 | 0 | DISABLE | 1 |
| GlobeNewswire Financing Agreements | 0 | 0 | 0 | 0 | DISABLE | 1 |
| PRNewswire Acquisitions Mergers And Takeovers | 0 | 0 | 0 | 0 | DISABLE | 1 |
| PRNewswire Bankruptcy | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Stock Offering | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Joint Ventures | 20 | 0 | 0 | 20 | DISABLE | 0 |
| GlobeNewswire Joint Venture | 20 | 0 | 0 | 20 | DISABLE | 0 |
| GlobeNewswire Restructuring , Recapitalization | 20 | 2 | 0 | 20 | NEEDS_REVIEW | 0 |
| GlobeNewswire Bankruptcy | 20 | 0 | 0 | 20 | DISABLE | 0 |
| GlobeNewswire Changes In Share Capital And Votes | 20 | 0 | 0 | 20 | DISABLE | 0 |
| GlobeNewswire Corporate Action | 20 | 1 | 0 | 20 | NEEDS_REVIEW | 0 |
| GlobeNewswire Press Releases | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Financing Agreements | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Private Placement | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Restructuring Recapitalization | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Shareholder Activism | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Shareholder Meetings | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Venture Capital | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Banking Financial Services | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Contracts | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Corporate Expansion | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire All News Releases From Pr Newswire News | 20 | 0 | 0 | 20 | DISABLE | 0 |
| SEC Press Releases | 20 | 0 | 0 | 20 | DISABLE | 0 |
| Newsfile Global Last25stories | 20 | 0 | 0 | 20 | DISABLE | 0 |
| Newsfile Industry Banking Financial Services | 10 | 0 | 0 | 10 | NEEDS_REVIEW | 0 |

## Batch AI Candidates Preview

- **eligibleCount:** 0
- **requestedLimit:** 25

- No promising groups need AI review right now.

## Top Deal Groups

### Triller Group (Nasdaq: ILLR) to Acquire Significant SpaceX Position as a Strategic Treasury Asset

- **groupKey:** names:triller group nasdaq illr:significant spacex position as a strategic treasury asset
- **buyer:** Triller Group (Nasdaq: ILLR)
- **target:** Significant SpaceX Position as a Strategic Treasury Asset
- **buyerTicker/CIK:** ILLR / 1769624
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/25/3317519/0/en/Triller-Group-Nasdaq-ILLR-to-Acquire-Significant-SpaceX-Position-as-a-Strategic-Treasury-Asset.html

### Nightfood Holdings Signs LOI to Acquire 51% of Jiun Jiang Enterprise

- **groupKey:** target-cik:1593001
- **buyer:** Nightfood Holdings Signs LOI
- **target:** 51% of Jiun Jiang Enterprise
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** NGTF / 1593001
- **priority:** HIGH
- **dealRelevance:** PRIVATE_COMPANY_ACQUISITION
- **tradability:** NOT_TRADABLE
- **dealStage:** RUMOR_OR_EXPLORATION
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/25/3317515/0/en/Nightfood-Holdings-Signs-LOI-to-Acquire-51-of-Jiun-Jiang-Enterprise.html

### DEFSEC Technologies Announces CAD$2.5 Million Registered Direct Offering

- **groupKey:** target-cik:1889823
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** DFSC / 1889823
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/defsec-technologies-announces-cad2-5-million-registered-direct-offering-302810108.html

### Merck KGaA, Darmstadt, Germany, Agrees to Acquire Bio-Techne, Strengthening Leadership Position in Fast-Growing Life Sciences Markets

- **groupKey:** target-cik:842023
- **buyer:** Merck KGaA, Darmstadt, Germany, Agrees
- **target:** Bio-Techne, Strengthening Leadership Position
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** TECH / 842023
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/merck-kgaa-darmstadt-germany-agrees-to-acquire-bio-techne-strengthening-leadership-position-in-fast-growing-life-sciences-markets-302810602.html

### Passage Bio and Remix Therapeutics Announce Merger Agreement

- **groupKey:** target-cik:1787297
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** PASG / 1787297
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** RUMOR_OR_EXPLORATION
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/24/3317152/0/en/Passage-Bio-and-Remix-Therapeutics-Announce-Merger-Agreement.html

### True Green Capital Management LLC Acquires 20.3 Megawatts of Operating Solar Assets

- **groupKey:** title:true green capital management 20 3 megawatts of operating solar assets
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** UNKNOWN
- **dealTiming:** UNKNOWN
- **review:** PENDING / -
- **evidenceCount:** 2
- **warnings:** Multiple related signals found for this deal
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/true-green-capital-management-llc-acquires-20-3-megawatts-of-operating-solar-assets-302809495.html
  - https://www.prnewswire.com/news-releases/true-green-capital-management-llc-acquires-20-3-megawatts-of-operating-solar-assets-302809487.html

### GD Culture Group Limited Announces Approximately $5.45 Million Registered Direct Offering of Common Stock Priced At-The-Market Under Nasdaq Rules

- **groupKey:** target-cik:1641398
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** GDC / 1641398
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/24/3316891/0/en/GD-Culture-Group-Limited-Announces-Approximately-5-45-Million-Registered-Direct-Offering-of-Common-Stock-Priced-At-The-Market-Under-Nasdaq-Rules.html

### Onconetix Highlights Realbotix’s Launch of Humanoid Robot and AI Teachers Assistant Pilot Program in New York State School District

- **groupKey:** target-cik:1782107
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** ONCO / 1782107
- **priority:** HIGH
- **dealRelevance:** PRIVATE_COMPANY_ACQUISITION
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/24/3316830/0/en/Onconetix-Highlights-Realbotix-s-Launch-of-Humanoid-Robot-and-AI-Teachers-Assistant-Pilot-Program-in-New-York-State-School-District.html

### Esquire Financial Holdings, Inc. and Signature Bancorporation Inc. Receive Stockholder Approvals for Merger

- **groupKey:** target-cik:1531031
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** ESQ / 1531031
- **priority:** HIGH
- **dealRelevance:** LAW_FIRM_OR_SHAREHOLDER_ALERT
- **tradability:** NOT_TRADABLE
- **dealStage:** LITIGATION_OR_LAW_FIRM_UPDATE
- **dealTiming:** NOISE
- **review:** PENDING / -
- **evidenceCount:** 2
- **warnings:** Multiple related signals found for this deal
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/esquire-financial-holdings-inc-and-signature-bancorporation-inc-receive-stockholder-approvals-for-merger-302808642.html
  - https://www.prnewswire.com/news-releases/esquire-financial-holdings-inc-and-signature-bancorporation-inc-announce-final-exchange-ratio-for-proposed-merger-302807272.html

### EXL to acquire iMerit, advancing its leadership in enterprise AI by adding foundation model expertise and technology

- **groupKey:** names:exl:imerit advancing its leadership
- **buyer:** EXL
- **target:** iMerit, advancing its leadership
- **buyerTicker/CIK:** EXLS / 1297989
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** UNKNOWN
- **tradability:** MEDIUM
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/24/3316801/9060/en/EXL-to-acquire-iMerit-advancing-its-leadership-in-enterprise-AI-by-adding-foundation-model-expertise-and-technology.html

### Evolve Royalties Enters Into Definitive Agreement in Connection with Previously Announced Acquisition of a Royalty on the Sunnyside Project in Arizona, USA

- **groupKey:** title:evolve royalties into in connection previously announced of royalty on sunnyside project in arizona usa
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** RSS signal is alert eligible
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/24/3316792/0/en/Evolve-Royalties-Enters-Into-Definitive-Agreement-in-Connection-with-Previously-Announced-Acquisition-of-a-Royalty-on-the-Sunnyside-Project-in-Arizona-USA.html

### Bravada Closes Non-Brokered Private Placement

- **groupKey:** title:bravada closes non brokered private placement
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** UNKNOWN
- **dealTiming:** UNKNOWN
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.newsfilecorp.com/release/302591/Bravada-Closes-NonBrokered-Private-Placement

### ROC to Acquire Zuccaro Technical Consulting; Expands ROC Evidence and Vision AI Capabilities Creating Robust End-to-End Investigative Platform

- **groupKey:** names:roc:zuccaro technical consulting expands roc evidence and vision ai capabilities creating robust end to end investigative platform
- **buyer:** ROC
- **target:** Zuccaro Technical Consulting; Expands ROC Evidence and Vision AI Capabilities Creating Robust End-to-End Investigative Platform
- **buyerTicker/CIK:** ROC / 2077709
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** LAW_FIRM_OR_SHAREHOLDER_ALERT
- **tradability:** NOT_TRADABLE
- **dealStage:** LITIGATION_OR_LAW_FIRM_UPDATE
- **dealTiming:** NOISE
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/24/3316725/0/en/ROC-to-Acquire-Zuccaro-Technical-Consulting-Expands-ROC-Evidence-and-Vision-AI-Capabilities-Creating-Robust-End-to-End-Investigative-Platform.html

### PhilWeb Secures ₱2.02 Billion Strategic Investment from Lance Y. Gokongwei to Accelerate AI-Driven Technology Expansion

- **groupKey:** title:philweb secures 2 02 billion strategic investment from lance y gokongwei accelerate ai driven technology expansion
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/philweb-secures-2-02-billion-strategic-investment-from-lance-y-gokongwei-to-accelerate-ai-driven-technology-expansion-302807662.html

### SELECTIS HEALTH, INC. ENTERS INTO AGREEMENT TO BE ACQUIRED BY BLACK PEARL

- **groupKey:** target-cik:727346
- **buyer:** BLACK PEARL
- **target:** SELECTIS HEALTH, INC.
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** GBCS / 727346
- **priority:** HIGH
- **dealRelevance:** PRIVATE_COMPANY_ACQUISITION
- **tradability:** LOW
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/23/3316460/0/en/SELECTIS-HEALTH-INC-ENTERS-INTO-AGREEMENT-TO-BE-ACQUIRED-BY-BLACK-PEARL.html

### Zodiac Partners II, LLC Announces Tender Offer Results, Raises Its Offer Price to $0.84 Per Share, Commits Additional Equity, and Extends the Expiration Date

- **groupKey:** title:zodiac partners ii tender offer results raises its offer price 0 84 per share commits additional equity extends expiration date
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** PRIVATE_COMPANY_ACQUISITION
- **tradability:** LOW
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/23/3315931/0/en/Zodiac-Partners-II-LLC-Announces-Tender-Offer-Results-Raises-Its-Offer-Price-to-0-84-Per-Share-Commits-Additional-Equity-and-Extends-the-Expiration-Date.html

### PLATINUM EQUITY TO ACQUIRE TANGENT TECHNOLOGIES

- **groupKey:** names:platinum equity:tangent technologies
- **buyer:** PLATINUM EQUITY
- **target:** TANGENT TECHNOLOGIES
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** PRIVATE_COMPANY_ACQUISITION
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/platinum-equity-to-acquire-tangent-technologies-302807057.html

### The Sterling Group Agrees to Sell Tangent Technologies to Platinum Equity

- **groupKey:** title:sterling group agrees sell tangent technologies platinum equity
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/the-sterling-group-agrees-to-sell-tangent-technologies-to-platinum-equity-302807240.html

### Boundless Bio and Serapha Bio Announce Merger Agreement and $230 Million Concurrent Private Placement

- **groupKey:** target-cik:1782303
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** BOLD / 1782303
- **priority:** HIGH
- **dealRelevance:** LAW_FIRM_OR_SHAREHOLDER_ALERT
- **tradability:** NOT_TRADABLE
- **dealStage:** LITIGATION_OR_LAW_FIRM_UPDATE
- **dealTiming:** NOISE
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/23/3315846/0/en/Boundless-Bio-and-Serapha-Bio-Announce-Merger-Agreement-and-230-Million-Concurrent-Private-Placement.html

### Bristow Group to Acquire Berry Aviation, Expanding Government Services Platform

- **groupKey:** names:bristow group:berry aviation expanding government services platform
- **buyer:** Bristow Group
- **target:** Berry Aviation, Expanding Government Services Platform
- **buyerTicker/CIK:** VTOL / 1525221
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** PRIVATE_COMPANY_ACQUISITION
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/bristow-group-to-acquire-berry-aviation-expanding-government-services-platform-302807657.html

### SUNSTONE HOTEL INVESTORS ENTERS INTO AN AGREEMENT TO SELL HYATT REGENCY SAN FRANCISCO

- **groupKey:** target-cik:1295810
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** SHO / 1295810
- **priority:** HIGH
- **dealRelevance:** UNKNOWN
- **tradability:** MEDIUM
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/sunstone-hotel-investors-enters-into-an-agreement-to-sell-hyatt-regency-san-francisco-302807029.html

### Energy Fuels Announces Definitive Agreement to Acquire VAC for $1.9 Billion Equity Value

- **groupKey:** names:energy fuels:vac
- **buyer:** Energy Fuels
- **target:** VAC
- **buyerTicker/CIK:** UUUU / 1385849
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/energy-fuels-announces-definitive-agreement-to-acquire-vac-for-1-9-billion-equity-value-302807538.html

### Apogee Therapeutics, Inc. 8-K

- **groupKey:** sec:1974640:0001140361-26-025841
- **buyer:** -
- **target:** Apogee Therapeutics, Inc.
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / 0001974640
- **priority:** HIGH
- **dealRelevance:** -
- **tradability:** -
- **dealStage:** -
- **dealTiming:** -
- **review:** PENDING / -
- **evidenceCount:** 6
- **warnings:** Multiple related signals found for this deal
- evidenceUrls:
  - https://www.sec.gov/Archives/edgar/data/1974640/000114036126025841/ef20076542_8k.htm
  - https://www.sec.gov/Archives/edgar/data/1974640/000114036126025844/ef20076505_8k.htm
  - https://www.sec.gov/Archives/edgar/data/1974640/000119312526269650/apge-20260609.htm
  - https://www.sec.gov/Archives/edgar/data/1974640/000110465926066572/tm2615568d1_8k.htm
  - https://www.sec.gov/Archives/edgar/data/1974640/000110465926066575/tm2615568d2_8k.htm
  - https://www.sec.gov/Archives/edgar/data/1974640/000119312526215683/apge-20260511.htm

### AbbVie Inc. 8-K

- **groupKey:** sec:1551152:0001104659-26-076067
- **buyer:** -
- **target:** AbbVie Inc.
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / 0001551152
- **priority:** HIGH
- **dealRelevance:** -
- **tradability:** -
- **dealStage:** -
- **dealTiming:** -
- **review:** PENDING / -
- **evidenceCount:** 4
- **warnings:** Multiple related signals found for this deal
- evidenceUrls:
  - https://www.sec.gov/Archives/edgar/data/1551152/000110465926076067/tm2618365d1_8k.htm
  - https://www.sec.gov/Archives/edgar/data/1551152/000110465926059484/tm2614276d1_8k.htm
  - https://www.sec.gov/Archives/edgar/data/1551152/000155115226000013/abbv-20260429.htm
  - https://www.sec.gov/Archives/edgar/data/1551152/000155115226000011/abbv-20260403.htm

### High Templar Tech Announces Preliminary Results of Modified Dutch Auction Tender Offer

- **groupKey:** target-cik:1692705
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** HTT / 1692705
- **priority:** MEDIUM
- **dealRelevance:** PUBLIC_STOCK_MERGER
- **tradability:** LOW
- **dealStage:** UNKNOWN
- **dealTiming:** UNKNOWN
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/high-templar-tech-announces-preliminary-results-of-modified-dutch-auction-tender-offer-302810639.html

### $HAREHOLDER ALERT: The M&A Class Action Firm Continues to Investigate the Merger--TMHC, NUVL, AXTA, and PFLC

- **groupKey:** title:hareholder alert m class action firm continues investigate tmhc nuvl axta pflc
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** MEDIUM
- **dealRelevance:** LAW_FIRM_OR_SHAREHOLDER_ALERT
- **tradability:** NOT_TRADABLE
- **dealStage:** LITIGATION_OR_LAW_FIRM_UPDATE
- **dealTiming:** NOISE
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/hareholder-alert-the-ma-class-action-firm-continues-to-investigate-the-mergertmhc-nuvl-axta-and-pflc-302809614.html

### Fiserv Announces Results of Tender Offers for Any and All of its Outstanding 5.150% Senior Notes due 2027 and 4.400% Senior Notes due 2049

- **groupKey:** target-cik:798354
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** FISV / 798354
- **priority:** MEDIUM
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** UNKNOWN
- **dealTiming:** UNKNOWN
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/24/3316672/0/en/Fiserv-Announces-Results-of-Tender-Offers-for-Any-and-All-of-its-Outstanding-5-150-Senior-Notes-due-2027-and-4-400-Senior-Notes-due-2049.html

### FirstCash to Acquire Ramsdens, a Leading Pawn, Retail and Financial Services Operator in the United Kingdom

- **groupKey:** names:firstcash:ramsdens a leading pawn retail and financial services operator
- **buyer:** FirstCash
- **target:** Ramsdens, a Leading Pawn, Retail and Financial Services Operator
- **buyerTicker/CIK:** FCFS / 840489
- **targetTicker/CIK:** - / -
- **priority:** MEDIUM
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** UNKNOWN
- **dealTiming:** UNKNOWN
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/23/3315718/0/en/FirstCash-to-Acquire-Ramsdens-a-Leading-Pawn-Retail-and-Financial-Services-Operator-in-the-United-Kingdom.html

### Tesla, Inc. 8-K

- **groupKey:** sec:1318605:0001628280-26-026551
- **buyer:** -
- **target:** Tesla, Inc.
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / 0001318605
- **priority:** LOW
- **dealRelevance:** -
- **tradability:** -
- **dealStage:** -
- **dealTiming:** -
- **review:** PENDING / -
- **evidenceCount:** 2
- **warnings:** Multiple related signals found for this deal
- evidenceUrls:
  - https://www.sec.gov/Archives/edgar/data/1318605/000162828026026551/tsla-20260422.htm
  - https://www.sec.gov/Archives/edgar/data/1318605/000162828026022956/tsla-20260402.htm

### Apple Inc. 8-K

- **groupKey:** sec:320193:0000320193-26-000011
- **buyer:** -
- **target:** Apple Inc.
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / 0000320193
- **priority:** LOW
- **dealRelevance:** -
- **tradability:** -
- **dealStage:** -
- **dealTiming:** -
- **review:** PENDING / -
- **evidenceCount:** 2
- **warnings:** Multiple related signals found for this deal
- evidenceUrls:
  - https://www.sec.gov/Archives/edgar/data/320193/000032019326000011/aapl-20260430.htm
  - https://www.sec.gov/Archives/edgar/data/320193/000114036126015711/ef20071035_8k.htm

## Recent Scan Runs

- #1190 | SUCCESS | MANUAL | fetched=0 | candidates=0 | duplicates=0 | finishedAt=null
- #1189 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=490 | finishedAt=2026-06-25T13:26:27.897533Z
- #1188 | SUCCESS | MANUAL | fetched=470 | candidates=0 | duplicates=468 | finishedAt=2026-06-25T13:26:08.408985Z
- #1187 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=474 | finishedAt=2026-06-25T13:21:28.741983Z
- #1186 | FAILED | SCHEDULED | fetched=0 | candidates=0 | duplicates=0 | finishedAt=2026-06-25T13:16:11.877134Z
- #1185 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=488 | finishedAt=2026-06-25T13:10:55.849148Z
- #1184 | SUCCESS | MANUAL | fetched=490 | candidates=0 | duplicates=474 | finishedAt=2026-06-25T13:10:02.687045Z
- #1183 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=450 | finishedAt=2026-06-25T13:04:18.520843Z
- #1182 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=482 | finishedAt=2026-06-25T12:59:01.532445Z
- #1181 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=481 | finishedAt=2026-06-25T12:53:31.583338Z

## Known Warnings

- Latest scan has no finishedAt timestamp.
- No strict AI candidates are available right now.
- Scheduled Full Refresh is disabled; Full Refresh runs only on manual click.
