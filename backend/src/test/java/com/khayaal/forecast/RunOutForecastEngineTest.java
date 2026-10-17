package com.khayaal.forecast;

import com.khayaal.schedule.ScheduleRule;
import com.khayaal.stock.StockLot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunOutForecastEngineTest {
    private final RunOutForecastEngine engine = new RunOutForecastEngine();

    @Test
    void matchesKnownValueForecastAndThreeDayReorderDate() {
        var result = engine.forecast(request("10", null, BigDecimal.ONE));
        assertThat(result.lastCoveredDate()).isEqualTo(LocalDate.parse("2026-01-05"));
        assertThat(result.runOutDate()).isEqualTo(LocalDate.parse("2026-01-06"));
        assertThat(result.reorderBy()).isEqualTo(LocalDate.parse("2026-01-03"));
        assertThat(result.daysLeft()).isEqualTo(5);
    }

    @Test
    void purchaseAddsToBalanceAndMovesRunOutDate() {
        var four = engine.forecast(request("4", null, BigDecimal.ONE));
        var fourteen = engine.forecast(request("14", null, BigDecimal.ONE));
        assertThat(four.runOutDate()).isEqualTo(LocalDate.parse("2026-01-03"));
        assertThat(fourteen.runOutDate()).isEqualTo(LocalDate.parse("2026-01-08"));
    }

    @Test
    void expiryDuringSimulationMakesStockUnavailable() {
        var result = engine.forecast(request("100", LocalDate.parse("2026-01-02"), BigDecimal.ONE));
        assertThat(result.runOutDate()).isEqualTo(LocalDate.parse("2026-01-03"));
    }

    @Test
    void courseEndingBeforeStockReturnsNoRunOutDate() {
        ScheduleRule course = ScheduleRule.daily(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), List.of(LocalTime.of(8, 0)));
        var result = engine.forecast(new ForecastRequest(course, ZoneId.of("UTC"), List.of(lot("10", null)), BigDecimal.ONE,
                Instant.parse("2026-01-01T07:00:00Z"), BigDecimal.ONE, 3));
        assertThat(result.runOutDate()).isNull();
        assertThat(result.lastCoveredDate()).isEqualTo(LocalDate.parse("2026-01-02"));
    }

    @Test
    void lowerAdherenceProducesLaterLikelyRunOutWithoutChangingPlannedAlertDate() {
        var result = engine.forecast(request("10", null, new BigDecimal("0.50")));
        assertThat(result.runOutDate()).isEqualTo(LocalDate.parse("2026-01-06"));
        assertThat(result.expectedRunOutDate()).isEqualTo(LocalDate.parse("2026-01-11"));
    }

    @Test
    void expectedForecastSupportsRatiosThatDoNotProduceQuarterUnitBalances() {
        var result = engine.forecast(request("10", null, new BigDecimal("0.80")));
        assertThat(result.expectedRunOutDate()).isEqualTo(LocalDate.parse("2026-01-07"));
    }

    private ForecastRequest request(String balance, LocalDate expiry, BigDecimal adherence) {
        var rule = ScheduleRule.daily(LocalDate.parse("2026-01-01"), null, List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)));
        return new ForecastRequest(rule, ZoneId.of("UTC"), List.of(lot(balance, expiry)), BigDecimal.ONE,
                Instant.parse("2026-01-01T07:00:00Z"), adherence, 3);
    }

    private StockLot lot(String units, LocalDate expiry) {
        return new StockLot("lot", new BigDecimal(units), expiry, Instant.parse("2026-01-01T00:00:00Z"));
    }
}

