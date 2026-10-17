package com.khayaal.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record StockLot(String lotId, BigDecimal remainingUnits, LocalDate expiryDate, Instant purchasedAt) {
    public StockLot {
        if (lotId == null || lotId.isBlank()) throw new IllegalArgumentException("lotId is required");
        Objects.requireNonNull(purchasedAt, "purchasedAt is required");
        remainingUnits = UnitQuantity.requireNonNegativeQuarterUnits(remainingUnits, "remainingUnits");
    }
}

