package com.khayaal.forecast;

import com.khayaal.schedule.ScheduleRule;
import com.khayaal.stock.StockLot;
import com.khayaal.stock.UnitQuantity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public record ForecastRequest(
        ScheduleRule scheduleRule,
        ZoneId recipientZone,
        List<StockLot> stockLots,
        BigDecimal unitsPerDose,
        Instant forecastStart,
        BigDecimal trailingAdherenceRatio,
        int leadTimeDays
) {
    public ForecastRequest {
        Objects.requireNonNull(scheduleRule, "scheduleRule is required");
        Objects.requireNonNull(recipientZone, "recipientZone is required");
        stockLots = List.copyOf(stockLots);
        unitsPerDose = UnitQuantity.requirePositiveQuarterUnits(unitsPerDose, "unitsPerDose");
        Objects.requireNonNull(forecastStart, "forecastStart is required");
        if (trailingAdherenceRatio == null || trailingAdherenceRatio.signum() < 0 || trailingAdherenceRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("trailingAdherenceRatio must be between zero and one");
        }
        if (leadTimeDays < 0) throw new IllegalArgumentException("leadTimeDays must not be negative");
    }
}

