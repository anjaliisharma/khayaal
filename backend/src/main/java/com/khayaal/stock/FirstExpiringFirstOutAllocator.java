package com.khayaal.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FirstExpiringFirstOutAllocator {
    private static final Comparator<StockLot> FEFO_ORDER = Comparator
            .comparing(StockLot::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockLot::purchasedAt)
            .thenComparing(StockLot::lotId);

    public LotConsumptionResult consume(List<StockLot> lots, BigDecimal requestedUnits, LocalDate consumptionDate) {
        if (requestedUnits == null || requestedUnits.signum() <= 0) {
            throw new IllegalArgumentException("requestedUnits must be positive");
        }
        List<StockLot> ordered = lots.stream().sorted(FEFO_ORDER).toList();
        Map<String, BigDecimal> remainingByLot = new HashMap<>();
        ordered.forEach(lot -> remainingByLot.put(lot.lotId(), lot.remainingUnits()));
        List<LotAllocation> allocations = new ArrayList<>();
        BigDecimal unmet = requestedUnits;

        for (StockLot lot : ordered) {
            if (unmet.signum() == 0) break;
            if (lot.expiryDate() != null && lot.expiryDate().isBefore(consumptionDate)) continue;
            BigDecimal available = remainingByLot.get(lot.lotId());
            BigDecimal allocated = available.min(unmet);
            if (allocated.signum() > 0) {
                allocations.add(new LotAllocation(lot.lotId(), allocated));
                remainingByLot.put(lot.lotId(), available.subtract(allocated));
                unmet = unmet.subtract(allocated);
            }
        }
        List<StockLot> updated = lots.stream()
                .map(lot -> new StockLot(lot.lotId(), remainingByLot.get(lot.lotId()), lot.expiryDate(), lot.purchasedAt()))
                .toList();
        return new LotConsumptionResult(List.copyOf(allocations), updated, unmet);
    }
}

