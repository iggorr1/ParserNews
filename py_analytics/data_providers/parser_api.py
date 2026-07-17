import requests
from typing import List, Dict, Any
from core.deal import Deal

def fetch_deals(api_key: str, api_url: str) -> List[Deal]:
    """
    Fetches deals from the ParserNews API and converts them into Deal objects.
    """
    print(f"[INFO] Fetching deals from {api_url}...")
    deals_to_return = []
    try:
        response = requests.get(
            api_url,
            headers={"X-API-Key": api_key},
            params={"minPriority": "HIGH", "withPrice": "true"},
        )
        response.raise_for_status()
        
        deals_list = response.json().get("deals", [])
        print(f"[INFO] Successfully fetched {len(deals_list)} deals.")

        for deal_data in deals_list:
            if deal_data.get("targetTicker") and deal_data.get("offerPrice"):
                deals_to_return.append(
                    Deal(
                        title=deal_data.get("title"),
                        target_company=deal_data.get("targetCompany"),
                        target_ticker=deal_data.get("targetTicker"),
                        offer_price=deal_data.get("offerPrice"),
                        raw_data=deal_data
                    )
                )
        return deals_to_return
        
    except requests.exceptions.RequestException as err:
        print(f"[ERROR] An error occurred while fetching deals: {err}")
    return []
