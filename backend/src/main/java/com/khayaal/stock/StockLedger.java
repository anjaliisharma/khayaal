package com.khayaal.stock;

import java.math.BigDecimal;
import java.util.List;

public final class StockLedger {
    public BigDecimal balance(List<StockLedgerEntry> entries) {
        return entries.stream().map(StockLedgerEntry::delta).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

