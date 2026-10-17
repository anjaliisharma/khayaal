package com.khayaal.forecast;

import com.khayaal.schedule.ScheduleExpander;
import com.khayaal.schedule.ScheduledOccurrence;
import com.khayaal.stock.FirstExpiringFirstOutAllocator;
import com.khayaal.stock.StockLot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RunOutForecastEngine {
    private static final int MAXIMUM_FORECAST_DAYS = 365;
    private final ScheduleExpander scheduleExpander;
    private final FirstExpiringFirstOutAllocator stockAllocator;

    public RunOutForecastEngine() {
        this(new ScheduleExpander(), new FirstExpiringFirstOutAllocator());
    }

    RunOutForecastEngine(ScheduleExpander scheduleExpander, FirstExpiringFirstOutAllocator stockAllocator) {
        this.scheduleExpander = scheduleExpander;
        this.stockAllocator = stockAllocator;
    }

    public RunOutForecast forecast(ForecastRequest request) {
        Instant endExclusive = request.forecastStart().plus(MAXIMUM_FORECAST_DAYS + 1L, ChronoUnit.DAYS);
        List<ScheduledOccurrence> occurrences = scheduleExpander.expand(
                request.scheduleRule(), request.recipientZone(), request.forecastStart(), endExclusive);
        Simulation planned = simulate(occurrences, request.stockLots(), request.unitsPerDose(), request.recipientZone());
        BigDecimal expectedUnits = request.unitsPerDose().multiply(request.trailingAdherenceRatio())
                .setScale(4, RoundingMode.HALF_UP);
        Simulation likely = expectedUnits.signum() == 0
                ? Simulation.covered(occurrences.isEmpty() ? null : occurrences.getLast().scheduledLocal().toLocalDate())
                : simulateExpectedConsumption(occurrences, request.stockLots(), expectedUnits, request.recipientZone());

        LocalDate startDate = request.forecastStart().atZone(request.recipientZone()).toLocalDate();
        LocalDate runOutDate = planned.runOutDate();
        long daysLeft = runOutDate == null ? MAXIMUM_FORECAST_DAYS : Math.max(0, ChronoUnit.DAYS.between(startDate, runOutDate));
        return new RunOutForecast(
                runOutDate,
                daysLeft,
                planned.lastCoveredDate(),
                likely.runOutDate(),
                runOutDate == null ? null : runOutDate.minusDays(request.leadTimeDays()));
    }

    private Simulation simulate(List<ScheduledOccurrence> occurrences, List<StockLot> initialLots, BigDecimal unitsPerDose, java.time.ZoneId zone) {
        List<StockLot> lots = initialLots;
        LocalDate lastCovered = null;
        for (ScheduledOccurrence occurrence : occurrences) {
            LocalDate date = occurrence.scheduledAt().atZone(zone).toLocalDate();
            var consumption = stockAllocator.consume(lots, unitsPerDose, date);
            if (!consumption.fullyCovered()) return new Simulation(date, lastCovered);
            lots = consumption.updatedLots();
            lastCovered = date;
        }
        return Simulation.covered(lastCovered);
    }

    private Simulation simulateExpectedConsumption(
            List<ScheduledOccurrence> occurrences,
            List<StockLot> initialLots,
            BigDecimal expectedUnitsPerDose,
            java.time.ZoneId zone) {
        List<ExpectedLotBalance> balances = initialLots.stream()
                .sorted(Comparator.comparing(StockLot::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StockLot::purchasedAt)
                        .thenComparing(StockLot::lotId))
                .map(ExpectedLotBalance::from)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        LocalDate lastCovered = null;

        for (ScheduledOccurrence occurrence : occurrences) {
            LocalDate date = occurrence.scheduledAt().atZone(zone).toLocalDate();
            BigDecimal unmet = expectedUnitsPerDose;
            for (int index = 0; index < balances.size() && unmet.signum() > 0; index++) {
                ExpectedLotBalance balance = balances.get(index);
                if (balance.expiryDate() != null && balance.expiryDate().isBefore(date)) continue;
                BigDecimal allocated = balance.remainingUnits().min(unmet);
                balances.set(index, balance.withRemainingUnits(balance.remainingUnits().subtract(allocated)));
                unmet = unmet.subtract(allocated);
            }
            if (unmet.signum() > 0) return new Simulation(date, lastCovered);
            lastCovered = date;
        }
        return Simulation.covered(lastCovered);
    }

    private record Simulation(LocalDate runOutDate, LocalDate lastCoveredDate) {
        static Simulation covered(LocalDate lastCoveredDate) { return new Simulation(null, lastCoveredDate); }
    }

    private record ExpectedLotBalance(BigDecimal remainingUnits, LocalDate expiryDate) {
        static ExpectedLotBalance from(StockLot lot) {
            return new ExpectedLotBalance(lot.remainingUnits(), lot.expiryDate());
        }

        ExpectedLotBalance withRemainingUnits(BigDecimal remainingUnits) {
            return new ExpectedLotBalance(remainingUnits, expiryDate);
        }
    }
}
