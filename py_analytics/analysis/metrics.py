from typing import Optional

def calculate_spread(offer_price: float, current_price: float) -> Optional[float]:
    """
    Calculates the M&A arbitrage spread as a percentage.
    
    Spread (%) = ( (Offer Price / Current Market Price) - 1 ) * 100
    """
    if current_price is None or current_price <= 0:
        return None
    return ((offer_price / current_price) - 1) * 100
