import os
import pandas as pd
from core.deal import Deal
import pytz

def export_deal_data(deal: Deal, base_output_dir: str = "output"):
    """
    Exports all enriched data for a single deal to a dedicated folder.
    """
    deal_dir = os.path.join(base_output_dir, deal.target_ticker)
    os.makedirs(deal_dir, exist_ok=True)
    
    print(f"\n  [EXPORT] Saving all data for {deal.target_ticker} to '{deal_dir}/'")

    ukraine_tz = pytz.timezone("Europe/Kiev")
    
    # 1. Save the summary
    summary_path = os.path.join(deal_dir, "summary.txt")
    with open(summary_path, 'w') as f:
        f.write(f"Analysis Summary for: {deal.title}\n")
        f.write("="*40 + "\n")
        f.write(f"Target: {deal.target_company} ({deal.target_ticker})\n")
        f.write(f"Exchange: {deal.exchange} (State: {deal.market_state})\n")
        f.write(f"Offer Price: ${deal.offer_price:.2f}\n")
        if deal.best_price:
            local_timestamp = deal.best_price_timestamp.astimezone(ukraine_tz).strftime('%Y-%m-%d %H:%M:%S %Z')
            f.write(f"Best Price: ${deal.best_price:.2f} (Source: {deal.best_price_source} at {local_timestamp})\n")
            f.write(f"Spread: {deal.spread_percentage:.2f}%\n")
        if deal.daily_volume_5wk is not None:
            avg_vol = deal.daily_volume_5wk['Volume'].mean()
            f.write(f"Avg Daily Vol (5wk): {avg_vol:,.0f}\n")
        if deal.market_cap:
            f.write(f"Market Cap: {deal.market_cap:,}\n")

    # 2. Save DataFrames to CSV
    if deal.historical_5w_4h is not None and not deal.historical_5w_4h.empty:
        deal.historical_5w_4h.to_csv(os.path.join(deal_dir, "historical_5w_4h.csv"))

    if deal.historical_5d_1h is not None and not deal.historical_5d_1h.empty:
        deal.historical_5d_1h.to_csv(os.path.join(deal_dir, "historical_5d_1h.csv"))
        
    if deal.daily_volume_5wk is not None and not deal.daily_volume_5wk.empty:
        deal.daily_volume_5wk.to_csv(os.path.join(deal_dir, "daily_volume_5wk.csv"))
        
    if deal.intraday_volume_profile is not None and not deal.intraday_volume_profile.empty:
        deal.intraday_volume_profile.to_csv(os.path.join(deal_dir, "intraday_volume.csv"))

    # 3. Save news to CSV
    if deal.news:
        news_df = pd.DataFrame(deal.news)
        news_df.to_csv(os.path.join(deal_dir, "news.csv"), index=False)
        
    print(f"  [EXPORT] Successfully saved all data.")
