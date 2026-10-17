package com.khayaal.adherence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AdherenceCalculator {
    public AdherenceSummary calculate(List<DoseRecord> records, LocalDate rangeStart, LocalDate rangeEndInclusive) {
        Objects.requireNonNull(records, "records are required");
        if (rangeEndInclusive.isBefore(rangeStart)) throw new IllegalArgumentException("range end must not precede range start");
        List<DoseRecord> inRange = records.stream()
                .filter(record -> !record.scheduledDate().isBefore(rangeStart) && !record.scheduledDate().isAfter(rangeEndInclusive))
                .toList();
        Map<DoseOutcome, Long> counts = inRange.stream().collect(Collectors.groupingBy(
                DoseRecord::outcome, () -> new EnumMap<>(DoseOutcome.class), Collectors.counting()));
        long onTime = counts.getOrDefault(DoseOutcome.TAKEN_ON_TIME, 0L);
        long late = counts.getOrDefault(DoseOutcome.TAKEN_LATE, 0L);
        long skipped = counts.getOrDefault(DoseOutcome.SKIPPED, 0L);
        long missed = counts.getOrDefault(DoseOutcome.MISSED, 0L);
        long adherenceDenominator = onTime + late + missed;
        Map<LocalDate, DailyCompletion> days = dailyCompletion(inRange);
        return new AdherenceSummary(onTime, late, skipped, missed,
                percentage(onTime, adherenceDenominator), percentage(late, adherenceDenominator),
                percentage(missed, adherenceDenominator), days,
                currentStreak(days, rangeEndInclusive), mostMissedTimes(inRange));
    }

    private Map<LocalDate, DailyCompletion> dailyCompletion(List<DoseRecord> records) {
        Map<LocalDate, List<DoseRecord>> byDate = records.stream()
                .filter(record -> record.outcome() != DoseOutcome.SKIPPED)
                .collect(Collectors.groupingBy(DoseRecord::scheduledDate));
        Map<LocalDate, DailyCompletion> result = new LinkedHashMap<>();
        byDate.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            long missed = entry.getValue().stream().filter(record -> record.outcome() == DoseOutcome.MISSED).count();
            DailyCompletion completion = missed == 0 ? DailyCompletion.FULLY_COMPLETED
                    : missed == entry.getValue().size() ? DailyCompletion.MISSED : DailyCompletion.PARTIALLY_COMPLETED;
            result.put(entry.getKey(), completion);
        });
        return Map.copyOf(result);
    }

    private int currentStreak(Map<LocalDate, DailyCompletion> days, LocalDate rangeEnd) {
        int streak = 0;
        for (LocalDate date = rangeEnd; days.get(date) == DailyCompletion.FULLY_COMPLETED; date = date.minusDays(1)) streak++;
        return streak;
    }

    private List<MissedTime> mostMissedTimes(List<DoseRecord> records) {
        Map<LocalTime, Long> counts = records.stream().filter(record -> record.outcome() == DoseOutcome.MISSED)
                .collect(Collectors.groupingBy(DoseRecord::scheduledTime, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<LocalTime, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new MissedTime(entry.getKey(), entry.getValue())).toList();
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
