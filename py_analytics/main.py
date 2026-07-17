# --- 1. Imports ---
import config
from core.deal import Deal
from data_providers.parser_api import fetch_deals
from data_providers.market_data import get_alpaca_clients, get_finnhub_client, enrich_deal_with_market_data
from reporting.file_exporter import export_deal_data

# --- 2. Main Execution Logic ---
def main():
    """
    Main function to orchestrate the entire M&A analysis pipeline.
    """
    print("\n" + "="*80)
    print("  M&A ARBITRAGE ANALYSIS SCRIPT")
    print("="*80)

    # --- I. INITIALIZATION & DATA COLLECTION ---
    print("\n[PHASE 1: INITIALIZATION & DATA COLLECTION]")
    
    # Initialize all API clients
    alpaca_clients = get_alpaca_clients(config.ALPACA_API_KEY, config.ALPACA_SECRET_KEY, config.ALPACA_PAPER_TRADING)
    finnhub_client = get_finnhub_client(config.FINNHUB_API_KEY)
    
    # Bundle clients into a dictionary for easy passing
    clients = {
        "trading": alpaca_clients[0] if alpaca_clients else None,
        "stock": alpaca_clients[1] if alpaca_clients else None,
        "finnhub": finnhub_client
    }

    if not clients["trading"] or not clients["stock"]:
        print("[ERROR] Failed to initialize Alpaca clients. Exiting.")
        return
    
    # Fetch initial deals
    deals = fetch_deals(config.DEALS_API_KEY, config.DEALS_API_URL)

    # --- FOCUSED TEST ON APH ---
    # print("\n[INFO] Running a focused test on a sample APH deal.")
    # aph_deal_data = {
    #     "title": "TEST DEAL: Carlisle to Acquire Amphenol (APH)",
    #     "target_company": "Amphenol Corporation",
    #     "target_ticker": "APH",
    #     "offer_price": 150.00,
    #     "raw_data": {}
    # }
    # deals.append(Deal(**aph_deal_data))
    # --- END OF TEST ---

    if not deals:
        print("\n[INFO] No deals found to analyze. Exiting.")
        return

    # --- II. DATA ENRICHMENT & ANALYSIS ---
    print("\n[PHASE 2: DATA ENRICHMENT & ANALYSIS]")
    
    for deal in deals:
        print("\n" + "-"*80)
        print(f"Analyzing Deal: {deal.title}")
        
        # --- Market Data Enrichment (Temporarily Disabled) ---
        # print("\n  --- Running Market Data Enrichment ---")
        # enrich_deal_with_market_data(deal, clients)
        
        # --- Regulatory Data Enrichment (Next Step) ---
        print("\n  --- Running Regulatory Data Enrichment (Placeholder) ---")
        # enrich_deal_with_regulatory_data(deal) # Placeholder for next phase
        
        # --- III. EXPORT RESULTS ---
        # export_deal_data(deal) # Also disabled as no new data is being added

    # --- IV. FINAL REPORTING ---
    print("\n" + "="*80)
    print("  ANALYSIS PIPELINE COMPLETE")
    print("="*80)

if __name__ == "__main__":
    main()
