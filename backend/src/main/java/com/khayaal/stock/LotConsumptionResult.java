package com.khayaal.stock;

import java.math.BigDecimal;
import java.util.List;

public record LotConsumptionResult(List<LotAllocation> allocations, List<StockLot> updatedLots, BigDecimal unmetUnits) {
    public boolean fullyCovered() { return unmetUnits.signum() == 0; }
}

