from alpaca.trading.client import TradingClient
from alpaca.data.historical import StockHistoricalDataClient
from alpaca.data.requests import StockLatestQuoteRequest
from typing import Optional, Tuple
from datetime import datetime
import yfinance as yf
import pandas as pd
import finnhub
from core.deal import Deal


# --- Client Initialization ---
def get_alpaca_clients(api_key: str, secret_key: str, paper: bool) -> Optional[Tuple[TradingClient, StockHistoricalDataClient]]:
    """Initializes and returns the Alpaca Trading and Market Data clients."""
    try:
        trading_client = TradingClient(api_key, secret_key, paper=paper)
        account = trading_client.get_account()
        print(f"[INFO] Successfully connected to Alpaca Trading. Account ID: {account.id}")

        stock_client = StockHistoricalDataClient(api_key, secret_key)
        print("[INFO] Successfully initialized Alpaca Market Data Client.")
        
        return trading_client, stock_client
    except Exception as e:
        print(f"[ERROR] Failed to connect to Alpaca: {e}")
        return None

def get_finnhub_client(api_key: str) -> Optional[finnhub.Client]:
    """Initializes and returns the Finnhub client."""
    try:
        if api_key == "YOUR_FINNHUB_API_KEY":
            print("[WARN] Finnhub API key is a placeholder. Skipping Finnhub client initialization.")
            return None
        finnhub_client = finnhub.Client(api_key=api_key)
        print("[INFO] Successfully initialized Finnhub Client.")
        return finnhub_client
    except Exception as e:
        print(f"[ERROR] Failed to connect to Finnhub: {e}")
        return None

# --- Main Orchestrator ---

def enrich_deal_with_market_data(deal: Deal, clients: dict):
    """
    Orchestrates the enrichment of a Deal object with all market data.
    """
    print(f"  [INFO] Starting market data enrichment for {deal.target_ticker}...")
    
    get_best_price(deal, clients)
    get_historical_data(deal)
    get_news(deal)
    get_volume_analysis(deal)
    get_financial_fundamentals(deal)

# --- Data Fetching Sub-functions ---

def get_best_price(deal: Deal, clients: dict):
    """
    Fetches the latest price from all available sources and selects the most recent one.
    """
    use_extended_hours = deal.market_state in ['PRE', 'POST']
    
    alpaca_price, alpaca_time_utc = _get_price_from_alpaca(deal.target_ticker, clients.get('stock'))
    yf_price, yf_time_utc = _get_price_from_yfinance(deal.target_ticker, use_extended_hours)
    fh_price, fh_time_utc = _get_price_from_finnhub(deal.target_ticker, clients.get('finnhub'))

    print(f"    [DEBUG] Alpaca Quote (UTC): Price=${alpaca_price}, Time={alpaca_time_utc}")
    print(f"    [DEBUG] yfinance Quote (UTC): Price=${yf_price}, Time={yf_time_utc}")
    print(f"    [DEBUG] Finnhub Quote (UTC): Price=${fh_price}, Time={fh_time_utc}")

    # Create a list of valid price sources with their data
    sources = []
    if alpaca_price and alpaca_time_utc:
        sources.append({"name": "Alpaca", "price": alpaca_price, "time": alpaca_time_utc})
    if yf_price and yf_time_utc:
        sources.append({"name": "yfinance", "price": yf_price, "time": yf_time_utc})
    if fh_price and fh_time_utc:
        sources.append({"name": "Finnhub", "price": fh_price, "time": fh_time_utc})

    if not sources:
        print(f"    [WARN] Could not retrieve a valid price for {deal.target_ticker}.")
        return

    # Find the source with the most recent timestamp
    best_source = max(sources, key=lambda x: x['time'])
    
    deal.best_price = best_source['price']
    deal.best_price_source = best_source['name']
    deal.best_price_timestamp = best_source['time']
    print(f"    [INFO] Best price source: {deal.best_price_source}")

    deal.calculate_spread()

# (Other get_* functions remain the same)
def get_historical_data(deal: Deal):
    try:
        use_extended_hours = deal.market_state in ['PRE', 'POST']
        stock = yf.Ticker(deal.target_ticker)
        deal.historical_5w_4h = stock.history(period="5wk", interval="4h", prepost=use_extended_hours)
        deal.historical_5d_1h = stock.history(period="5d", interval="1h", prepost=use_extended_hours)
        print(f"    [INFO] Fetched historical data for {deal.target_ticker}.")
    except Exception as e:
        print(f"    [ERROR] Failed to fetch historical data for {deal.target_ticker}: {e}")

def get_volume_analysis(deal: Deal):
    try:
        use_extended_hours = deal.market_state in ['PRE', 'POST']
        stock = yf.Ticker(deal.target_ticker)
        hist_35d = stock.history(period="35d", interval="1d")
        if not hist_35d.empty:
            deal.daily_volume_5wk = hist_35d[['Volume']]
            print(f"    [INFO] Fetched 5-week daily volume for {deal.target_ticker}.")
        intraday_data = stock.history(period="5d", interval="1m", prepost=use_extended_hours)
        if not intraday_data.empty:
            latest_day = intraday_data.index.max().date()
            deal.intraday_volume_profile = intraday_data[intraday_data.index.date == latest_day][['Volume']]
            print(f"    [INFO] Fetched intraday volume for {deal.target_ticker}.")
    except Exception as e:
        print(f"    [ERROR] Failed to perform volume analysis for {deal.target_ticker}: {e}")

def get_news(deal: Deal):
    try:
        stock = yf.Ticker(deal.target_ticker)
        deal.news = stock.news
        print(f"    [INFO] Fetched {len(deal.news)} news articles for {deal.target_ticker}.")
    except Exception as e:
        print(f"    [ERROR] Failed to fetch news for {deal.target_ticker}: {e}")

def get_financial_fundamentals(deal: Deal):
    try:
        stock = yf.Ticker(deal.target_ticker)
        info = stock.info
        deal.market_cap = info.get("marketCap")
        deal.pe_ratio = info.get("trailingPE")
        print(f"    [INFO] Fetched fundamentals for {deal.target_ticker}.")
    except Exception as e:
        print(f"    [ERROR] yfinance call failed for {deal.target_ticker}: {e}")

# --- Helper functions for get_best_price ---

def _get_price_from_alpaca(ticker: str, stock_client: StockHistoricalDataClient) -> Tuple[Optional[float], Optional[pd.Timestamp]]:
    if not stock_client: return None, None
    try:
        request_params = StockLatestQuoteRequest(symbol_or_symbols=ticker)
        latest_quote = stock_client.get_stock_latest_quote(request_params)
        quote_data = latest_quote.get(ticker)
        if quote_data:
            return quote_data.ask_price, quote_data.timestamp
        return None, None
    except Exception as e:
        print(f"    [DEBUG] Alpaca price fetch failed: {e}")
        return None, None

def _get_price_from_yfinance(ticker: str, use_extended_hours: bool) -> Tuple[Optional[float], Optional[pd.Timestamp]]:
    try:
        stock = yf.Ticker(ticker)
        data = stock.history(period="1d", interval="1m", prepost=use_extended_hours)
        if not data.empty:
            last_trade = data.iloc[-1]
            return last_trade['Close'], last_trade.name.tz_convert('UTC')
        return None, None
    except Exception as e:
        print(f"    [DEBUG] yfinance price fetch failed: {e}")
        return None, None

def _get_price_from_finnhub(ticker: str, finnhub_client: finnhub.Client) -> Tuple[Optional[float], Optional[pd.Timestamp]]:
    if not finnhub_client: return None, None
    try:
        quote = finnhub_client.quote(ticker)
        # Finnhub timestamp is a Unix timestamp, convert it to a timezone-aware datetime
        timestamp = pd.to_datetime(quote['t'], unit='s', utc=True)
        return quote['c'], timestamp # 'c' is the close price
    except Exception as e:
        print(f"    [DEBUG] Finnhub price fetch failed: {e}")
        return None, None
