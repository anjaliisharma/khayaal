package com.khayaal.stock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirstExpiringFirstOutAllocatorTest {
    private final FirstExpiringFirstOutAllocator allocator = new FirstExpiringFirstOutAllocator();

    @Test
    void consumesFirstExpiringLotAndCanSpanLots() {
        var lots = List.of(lot("A", "3", "2026-03-01", "2026-01-01T00:00:00Z"),
                lot("B", "10", "2026-06-01", "2026-01-02T00:00:00Z"));
        var first = allocator.consume(lots, new BigDecimal("2"), LocalDate.parse("2026-02-01"));
        assertThat(first.allocations()).containsExactly(new LotAllocation("A", new BigDecimal("2")));
        var second = allocator.consume(first.updatedLots(), new BigDecimal("2"), LocalDate.parse("2026-02-01"));
        assertThat(second.allocations()).containsExactly(
                new LotAllocation("A", BigDecimal.ONE), new LotAllocation("B", BigDecimal.ONE));
    }

    @Test
    void excludesExpiredLotsAndUsesUndatedLotsLast() {
        var lots = List.of(lot("expired", "5", "2026-02-01", "2026-01-01T00:00:00Z"),
                new StockLot("undated", new BigDecimal("3"), null, Instant.parse("2026-01-01T00:00:00Z")));
        var result = allocator.consume(lots, new BigDecimal("2"), LocalDate.parse("2026-02-02"));
        assertThat(result.allocations()).containsExactly(new LotAllocation("undated", new BigDecimal("2")));
    }

    @Test
    void reportsUnmetDemandAndRejectsInvalidDemand() {
        var result = allocator.consume(List.of(lot("A", "1", "2026-03-01", "2026-01-01T00:00:00Z")),
                new BigDecimal("2"), LocalDate.parse("2026-02-01"));
        assertThat(result.unmetUnits()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> allocator.consume(List.of(), BigDecimal.ZERO, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockLot("lot", new BigDecimal("0.10"), null, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quarter-unit");
    }

    private StockLot lot(String id, String units, String expiry, String purchased) {
        return new StockLot(id, new BigDecimal(units), LocalDate.parse(expiry), Instant.parse(purchased));
    }
}

