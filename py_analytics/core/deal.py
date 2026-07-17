from dataclasses import dataclass, field
from typing import Optional, Dict, Any, List
import pandas as pd

@dataclass
class Deal:
    """A central object to hold all information about a potential M&A deal."""
    # Initial data
    title: str
    target_company: str
    target_ticker: str
    offer_price: float
    raw_data: Dict[str, Any] = field(repr=False)

    # Exchange & Market State
    exchange: Optional[str] = None
    market_state: Optional[str] = None

    # Best Price & Spread
    best_price: Optional[float] = None
    best_price_source: Optional[str] = None
    best_price_timestamp: Optional[pd.Timestamp] = None # Stored in UTC
    spread_percentage: Optional[float] = None

    # Volume Analysis
    daily_volume_5wk: Optional[pd.DataFrame] = field(default=None, repr=False)
    intraday_volume_profile: Optional[pd.DataFrame] = field(default=None, repr=False)

    # Historical Data
    historical_5w_4h: Optional[pd.DataFrame] = field(default=None, repr=False)
    historical_5d_1h: Optional[pd.DataFrame] = field(default=None, repr=False)

    # News & Fundamentals
    news: List[Dict[str, Any]] = field(default_factory=list)
    market_cap: Optional[int] = None
    pe_ratio: Optional[float] = None

    def calculate_spread(self):
        """Calculates and sets the spread percentage based on the best available price."""
        if self.best_price is None or self.best_price <= 0:
            self.spread_percentage = None
        else:
            self.spread_percentage = ((self.offer_price / self.best_price) - 1) * 100
