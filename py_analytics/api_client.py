import requests
from typing import List, Dict, Any

DEALS_API_URL = "https://parsernews.wwwho.lol/api/export/deals"

def fetch_deals(api_key: str) -> List[Dict[str, Any]]:
    print(f"[INFO] Fetching deals from {DEALS_API_URL}...")
    try:
        response = requests.get(
            DEALS_API_URL,
            headers={"X-API-Key": api_key},
            params={"minPriority": "HIGH", "withPrice": "true"},
        )
        response.raise_for_status()
        
        deals_list = response.json().get("deals", [])
        
        print(f"[INFO] Successfully fetched {len(deals_list)} deals.")
        return deals_list
    except requests.exceptions.RequestException as err:
        print(f"[ERROR] An error occurred while fetching deals: {err}")
    return []
