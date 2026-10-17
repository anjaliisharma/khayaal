package com.khayaal.stock;

import java.math.BigDecimal;
import java.util.Objects;

public record StockLedgerEntry(BigDecimal delta) {
    public StockLedgerEntry { Objects.requireNonNull(delta, "delta is required"); }
}

