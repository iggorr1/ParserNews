# ParserNews Review Packet

- **Generated:** 2026-06-26T12:28:18.333296078Z

## App Status

- **health:** OK
- **scannerMonitoringEnabled:** true
- **alertDispatchEnabled:** false
- **telegramEnabled:** false
- **telegramConfigured:** false

### Latest Scan

- **id:** 1462
- **status:** SUCCESS
- **startedAt:** 2026-06-26T12:25:30.874965Z
- **finishedAt:** 2026-06-26T12:25:46.212815Z
- **triggerType:** SCHEDULED
- **totalFetched:** 490
- **candidatesFound:** 0
- **duplicatesSkipped:** 486

### Counts

- **savedArticles:** 5646
- **detectedCandidates:** 58
- **highCandidates:** 49
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

- **id:** 1462
- **status:** SUCCESS
- **startedAt:** 2026-06-26T12:25:30.874965Z
- **finishedAt:** 2026-06-26T12:25:46.212815Z

## SEC Discovery Status

- **enabled:** false
- **schedulerEnabled:** false
- **forms:** 8-K, SC TO-T, SC TO-I, SC 14D9, DEFM14A, PREM14A, 425, S-4
- **maxFilingsPerRun:** 50
- **fetchPrimaryDocument:** false
- **lastRunAt:** -
- **lastRunStatus:** -
- **warning:** SEC discovery is disabled.

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

- **totalGroups:** 27
- **pendingGroups:** 27
- **usefulGroups:** 0
- **ignoredGroups:** 0
- **highPriorityGroups:** 22
- **alertLikeGroups:** 2
- **groupedEvidenceTotal:** 39
- **averageEvidencePerGroup:** 1.44

### Review reasons

- none

### By relevance

- UNKNOWN: 6
- PUBLIC_PUBLIC_MERGER: 1
- PRIVATE_COMPANY_ACQUISITION: 1
- NOT_TRADABLE: 10
- PUBLIC_CASH_ACQUISITION: 2
- LAW_FIRM_OR_SHAREHOLDER_ALERT: 3

### By tradability

- MEDIUM: 7
- NOT_TRADABLE: 14
- HIGH: 2

### By stage

- UNKNOWN: 6
- INITIAL_ANNOUNCEMENT: 7
- DEFINITIVE_AGREEMENT: 5
- RUMOR_OR_EXPLORATION: 2
- LITIGATION_OR_LAW_FIRM_UPDATE: 3

### By timing

- UNKNOWN: 6
- EARLY: 14
- NOISE: 3

### By priority

- HIGH: 22
- MEDIUM: 3
- LOW: 2

## AI Review Summary

- **uniqueGroupsReviewed:** 9
- **totalAiReviewsSaved:** 12
- **duplicateHistoricalReviewsIgnored:** 3
- **goodSignalCount:** 3
- **notTradableCount:** 3
- **privateCompanyCount:** 2
- **falsePositiveCount:** 0
- **needsHumanReviewCount:** 1
- **unknownCount:** 0
- **highConfidenceCount:** 9
- **mediumConfidenceCount:** 0
- **lowConfidenceCount:** 0

### Latest AI Reviews

- target-cik:727346 | SELECTIS HEALTH, INC. ENTERS INTO AGREEMENT TO BE ACQUIRED BY BLACK PEARL | GOOD_SIGNAL / HIGH | The deal involves a publicly traded target (SELECTIS HEALTH, INC. ticker=GBCS) being acquired by BLACK PEARL with a cash acquisition structure at an initial announcement stage, making it an early and tradable public target signal suitable for human review.
- target-cik:842023 | Merck KGaA, Darmstadt, Germany, Agrees to Acquire Bio-Techne, Strengthening Leadership Position in Fast-Growing Life Sciences Markets | GOOD_SIGNAL / HIGH | The deal involves a public and tradable target (Bio-Techne), is a cash acquisition, and is at the initial announcement stage, making it a timely and relevant signal for review.
- target-cik:1926599 | target-cik:1926599 | NOT_TRADABLE / HIGH | The deal group does not specify a tradable buyer and involves a share redemption event rather than a straightforward acquisition with a cash or fixed-price offer. Although the target is public, the absence of a buyer ticker and the nature of the event make this less relevant as a useful M&A research signal for trading review.
- target-cik:1782107 | Onconetix Highlights Realbotix’s Launch of Humanoid Robot and AI Teachers Assistant Pilot Program in New York State School District | NEEDS_HUMAN_REVIEW / HIGH | The deal group lacks a clear buyer with ticker or cik information and does not explicitly mention a cash or fixed-price offer, making it an incomplete and unclear M&A signal for trading or deeper research purposes.
- target-cik:1551152 | target-cik:1551152 | GOOD_SIGNAL / HIGH | The deal involves a public, tradable target (AbbVie Inc.) with a cash acquisition, and it is at an initial announcement stage, making it an early and relevant M&A research signal. Multiple related signals and SEC filings support the validity and timing of this deal group.
- target-cik:2077709 | target-cik:2077709 | NOT_TRADABLE / HIGH | The target company is not tradable as it is a private technical consulting firm. The buyer ROC's details are incomplete and there is no indication of a public, tradable target or fixed-price offer. The signal is primarily a law firm/shareholder alert related to litigation or noise, not an actionable M&A tradable event.
- names:platinum equity:tangent technologies | names:platinum equity:tangent technologies | PRIVATE_COMPANY / HIGH | The target, Tangent Technologies, is a private company and not tradable. Although the acquisition is at the definitive agreement stage and early enough, the lack of a tradable public target reduces the usefulness for tradable M&A research signals.
- rss:194 | rss:194 | PRIVATE_COMPANY / HIGH | The deal involves a private company acquisition with no public ticker or CIK information and tradability is low.
- target-cik:1531031 | Esquire Financial Holdings, Inc. and Signature Bancorporation Inc. Receive Stockholder Approvals for Merger | NOT_TRADABLE / HIGH | The deal involves a final exchange ratio announcement with no public buyer ticker and is marked as not tradable, likely indicating limited tradability or insufficient public market information.

## Source Quality Audit

- **totalConfiguredSources:** 25
- **KEEP:** 0
- **NEEDS_REVIEW:** 4
- **DISABLE:** 21
- **strictCandidateCountTotal:** 0

| sourceName | fetched | candidates | strict | noise | recommendation | errors |
|---|---:|---:|---:|---:|---|---:|
| GlobeNewswire Mergers and Acquisitions | 20 | 0 | 0 | 20 | DISABLE | 0 |
| GlobeNewswire Financing Agreements | 20 | 0 | 0 | 20 | DISABLE | 0 |
| PRNewswire Acquisitions Mergers And Takeovers | 20 | 0 | 0 | 20 | DISABLE | 0 |
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
| Newsfile Global Last25stories | 20 | 1 | 0 | 19 | NEEDS_REVIEW | 0 |
| Newsfile Industry Banking Financial Services | 10 | 0 | 0 | 10 | NEEDS_REVIEW | 0 |

## Batch AI Candidates Preview

- **eligibleCount:** 0
- **requestedLimit:** 25

- No promising groups need AI review right now.

## Top Deal Groups

### United Dogecoin Purchases Dogecoin Miners and Secures Renewable Energy Data Centre Site

- **groupKey:** target-cik:1757499
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** SHPH / 1757499
- **priority:** HIGH
- **dealRelevance:** UNKNOWN
- **tradability:** MEDIUM
- **dealStage:** UNKNOWN
- **dealTiming:** UNKNOWN
- **review:** PENDING / -
- **evidenceCount:** 2
- **warnings:** Multiple related signals found for this deal
- evidenceUrls:
  - https://www.newsfilecorp.com/release/302884/United-Dogecoin-Purchases-Dogecoin-Miners-and-Secures-Renewable-Energy-Data-Centre-Site
  - https://www.newsfilecorp.com/release/302883/United-Dogecoin-Advances-Data-Centre-and-Power-Infrastructure-Strategy-to-Support-DOGE-Mining-and-AI-Hosting

### Calian Enters Definitive Agreement to Acquire Galaxy Broadband, Expanding Communications and Connectivity Capabilities Across Canada

- **groupKey:** names:calian:galaxy broadband expanding communications and connectivity capabilities across canada
- **buyer:** Calian
- **target:** Galaxy Broadband, Expanding Communications and Connectivity Capabilities Across Canada
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** UNKNOWN
- **tradability:** MEDIUM
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/25/3317980/0/en/Calian-Enters-Definitive-Agreement-to-Acquire-Galaxy-Broadband-Expanding-Communications-and-Connectivity-Capabilities-Across-Canada.html

### onsemi to Acquire Synaptics to Enable the Next Generation of Intelligent Systems for Physical AI

- **groupKey:** names:onsemi:synaptics to enable the next generation of intelligent systems
- **buyer:** onsemi
- **target:** Synaptics to Enable the Next Generation of Intelligent Systems
- **buyerTicker/CIK:** ON / 1097864
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** PUBLIC_PUBLIC_MERGER
- **tradability:** MEDIUM
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/25/3317941/0/en/onsemi-to-Acquire-Synaptics-to-Enable-the-Next-Generation-of-Intelligent-Systems-for-Physical-AI.html

### Premarket Acquires Maklare AI to Build Residential Real Estate's First Demand Intelligence Platform

- **groupKey:** title:premarket maklare ai build residential real estate s first demand intelligence platform
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** - / -
- **priority:** HIGH
- **dealRelevance:** PRIVATE_COMPANY_ACQUISITION
- **tradability:** NOT_TRADABLE
- **dealStage:** UNKNOWN
- **dealTiming:** UNKNOWN
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/premarket-acquires-maklare-ai-to-build-residential-real-estates-first-demand-intelligence-platform-302811053.html

### ASP Isotopes Announces Proposed Merger of Noble Africa with ENDRA Life Sciences and Approximately $50 Million Concurrent Private Placement Financing

- **groupKey:** target-cik:1921865
- **buyer:** -
- **target:** -
- **buyerTicker/CIK:** - / -
- **targetTicker/CIK:** ASPI / 1921865
- **priority:** HIGH
- **dealRelevance:** NOT_TRADABLE
- **tradability:** NOT_TRADABLE
- **dealStage:** DEFINITIVE_AGREEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/25/3317646/0/en/ASP-Isotopes-Announces-Proposed-Merger-of-Noble-Africa-with-ENDRA-Life-Sciences-and-Approximately-50-Million-Concurrent-Private-Placement-Financing.html

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
- **dealRelevance:** UNKNOWN
- **tradability:** MEDIUM
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
- **dealRelevance:** PUBLIC_CASH_ACQUISITION
- **tradability:** HIGH
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** RSS signal is alert eligible
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
- **dealRelevance:** UNKNOWN
- **tradability:** MEDIUM
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
- **evidenceCount:** 1
- **warnings:** 
- evidenceUrls:
  - https://www.prnewswire.com/news-releases/esquire-financial-holdings-inc-and-signature-bancorporation-inc-receive-stockholder-approvals-for-merger-302808642.html

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
- **warnings:** 
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
- **dealRelevance:** PUBLIC_CASH_ACQUISITION
- **tradability:** HIGH
- **dealStage:** INITIAL_ANNOUNCEMENT
- **dealTiming:** EARLY
- **review:** PENDING / -
- **evidenceCount:** 1
- **warnings:** RSS signal is alert eligible
- evidenceUrls:
  - https://www.globenewswire.com/news-release/2026/06/23/3316460/0/en/SELECTIS-HEALTH-INC-ENTERS-INTO-AGREEMENT-TO-BE-ACQUIRED-BY-BLACK-PEARL.html

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
- **dealRelevance:** UNKNOWN
- **tradability:** MEDIUM
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

- #1463 | SUCCESS | MANUAL | fetched=490 | candidates=0 | duplicates=489 | finishedAt=2026-06-26T12:28:16.834071Z
- #1462 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=486 | finishedAt=2026-06-26T12:25:46.212815Z
- #1461 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=488 | finishedAt=2026-06-26T12:20:30.885001Z
- #1460 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=489 | finishedAt=2026-06-26T12:15:11.646822Z
- #1459 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=489 | finishedAt=2026-06-26T12:09:59.720290Z
- #1458 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=459 | finishedAt=2026-06-26T12:04:46.359165Z
- #1457 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=490 | finishedAt=2026-06-26T11:59:28.133133Z
- #1456 | SUCCESS | MANUAL | fetched=490 | candidates=0 | duplicates=490 | finishedAt=2026-06-26T11:55:58.690473Z
- #1455 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=487 | finishedAt=2026-06-26T11:54:16.778593Z
- #1454 | SUCCESS | SCHEDULED | fetched=490 | candidates=0 | duplicates=490 | finishedAt=2026-06-26T11:49:03.503388Z

## Known Warnings

- No strict AI candidates are available right now.
- Scheduled Full Refresh is disabled; Full Refresh runs only on manual click.
- SEC Discovery is disabled; broad SEC discovery runs only when explicitly enabled.
