package com.khayaal.forecast;

import com.khayaal.schedule.ScheduleRule;
import com.khayaal.stock.StockLot;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastMonotonicityPropertyTest {
    @Property
    void additionalStockNeverMakesRunOutEarlier(@ForAll @IntRange(min = 1, max = 100) int stock,
                                                  @ForAll @IntRange(min = 1, max = 100) int added) {
        var earlier = forecast(stock);
        var later = forecast(stock + added);
        if (later != null) assertThat(later).isAfterOrEqualTo(earlier);
    }

    private LocalDate forecast(int units) {
        var rule = ScheduleRule.daily(LocalDate.parse("2026-01-01"), null, List.of(LocalTime.NOON));
        var lot = new StockLot("lot", BigDecimal.valueOf(units), null, Instant.EPOCH);
        return new RunOutForecastEngine().forecast(new ForecastRequest(rule, ZoneId.of("UTC"), List.of(lot), BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z"), BigDecimal.ONE, 3)).runOutDate();
    }
}
